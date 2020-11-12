package dev.nauber.esphomerccar


import android.util.Log
import com.faendir.rhino_android.RhinoAndroidHelper
import org.mozilla.javascript.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue


class Controller(val context: android.content.Context, val comm: Communication) : Runnable {
    val TAG = "Controller"
    var inpUser = ConcurrentHashMap<String, Float>()
    val runQueue = LinkedBlockingQueue<String>()

    val t = Thread(this)

    init {
        t.start()
    }

    val script = """

        outp = {};
        outp.display = "Hi from the controller, I am running because " + inp["reason"] ;
                
        if (typeof i == "undefined") {
            i = 0;
        }
        i++;
        outp.display += " i=" + i +"\n";
        
        outp.hbridge = [{"index":0, "strength":  inp.user.x * 0.6, "brake":false}, 
                        {"index":1, "strength": inp.user.y * 0.6, "brake":false},
                        {"index":2, "strength": inp.user.y * 0.4, "brake":false},
                        ]
        
    """.trimIndent()


    override fun run() {
        val rhinoAndroidHelper = RhinoAndroidHelper(context)
        val rhinoContext = rhinoAndroidHelper.enterContext();
        rhinoContext.optimizationLevel = 9;
        val scope = rhinoContext.initSafeStandardObjects();

        while (true) {
            val reason = runQueue.take()
            try {
                //prepare input

                val inpObject = NativeObject()
                val inpUserObject = NativeObject()
                inpUser.forEach(10) { k, v -> inpUserObject.defineProperty(k, v, 0) }

                inpObject.defineProperty("reason", reason, 0)
                inpObject.defineProperty("user", inpUserObject, 0)

                ScriptableObject.putProperty(scope, "inp", inpObject)

                rhinoContext.evaluateString(scope, script, "<control_script>", 1, null)

                val outp = scope.get("outp") as NativeObject

                val log = outp["log"].toString()
                Log.d(TAG, log)

                val display = outp["display"].toString()
                Log.d(TAG, display)

                val hbridge = outp["hbridge"] as? Iterable<*>
                hbridge?.forEach {
                    val hb = it as NativeObject
                    try {
                        val index = Context.jsToJava(hb["index"], ScriptRuntime.IntegerClass) as Int
                        val strength = try {
                            Context.jsToJava(hb["strength"], ScriptRuntime.FloatClass) as Float
                        } catch (e: EvaluatorException) {
                            0.0f
                        }
                        val brake = try {
                            Context.jsToJava(hb["brake"], ScriptRuntime.BooleanClass) as Boolean
                        } catch (e: EvaluatorException) {
                            false
                        }

                        comm.setHBridge(index = index, strength = strength, brake = brake)
                    } catch (t: EvaluatorException) {
                    }
                }

            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        Context.exit()
    }

    fun updateInput(map: Map<String, Float>) {
        inpUser.putAll(map)
        runQueue.put("userinput")
    }
}