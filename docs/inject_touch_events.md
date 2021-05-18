# Inject Touch Events

Injecting touch inputs to other apps in android is a complicate task. Even though Android provides multiple ways to inject input events, each one comes with a set of limitations. In this document I discuss my findings about the available APIs.

Here is an overview of the methods to simulate touch events I've discovered so far:

  - [Instrumentation](https://developer.android.com/reference/android/app/Instrumentation)
  - [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
  - [InputManager](https://developer.android.com/reference/android/hardware/input/InputManager)

A common limitation across the APIs is that they require the [INJECT_EVENTS permission](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/res/AndroidManifest.xml) permission in order to inject events to other apps. This is defined in android as a `signature` protection, which means that only apps signed with a system key can be granted such a permission. At a first glance, this limits the applicability of the APIs to custom ROMs. However, there is a trick which can be used via `adb` which will be discussed in the `InputManager` section below.

## Instrumentation

The `Instrumentation` API allows apps to easily inject touch events via the [sendPointerSync method]("https://developer.android.com/reference/android/app/Instrumentation#sendPointerSync(android.view.MotionEvent"). It is as easy as building an object and calling its method:

```

Instrumentation instr = new Instrumentation()
instr.sendPointerSync(motionEvent)

```

*Note*: the `sendPointerSync` method cannot be called from the main thread.

This successfully injects events into the same app but requires the `INJECT_EVENTS` permission to inject to other apps.

## AccessibilityService

Although looking very promising due to the lack of the `INJECT_EVENTS` permission, the `AccessibilityService` comes with a set of limitations which hugely inpact what can be done with it.

The accessibility service must be defined in the android manifest:

```
<service android:name=".MyAccessibilityService"
         android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
     <intent-filter>
         <action android:name="android.accessibilityservice.AccessibilityService" />
     </intent-filter>

     <meta-data
         android:name="android.accessibilityservice"
         android:resource="@xml/config_accessibilityservice" />
</service>
```

The `xml/config_accessibilityservice.xml` contains:

```
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityFlags="flagRequestFilterKeyEvents|flagReportViewIds|flagDefault"
    android:canPerformGestures="true"
    android:canRequestFilterKeyEvents="true"
    android:canRetrieveWindowContent="false"
    android:description="@string/accessibility_service_description"
    android:settingsActivity="com.package.MainActivity" />
```

The custom service can be implemented by subclassing `AccessibilityService`. The default methods implementation is just fine. The `AccessibilityService` is automatically instantiated provided that the user enables it from the Accessibility settings of the android device. Then the [dispatchGesture method]("https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler") can be called on the `AccessibilityService` instance to inject a [GestureDescription](https://developer.android.com/reference/android/accessibilityservice/GestureDescription). Building a GestureDescription requires calling the [GestureDescription.Builder.addStroke method]("https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder#addStroke(android.accessibilityservice.GestureDescription.StrokeDescription)"). A [StrokeDescription](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription) accepts a Path object, which represents the movement of the finger to simulate.

In short, a gesture is a collection of strokes, each one made of a path. The strokes can start at different times and have a different duration. Only a single gesture can be active at a time. The gesture is completed when all its strokes complete. The [GestureResultCallback](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.GestureResultCallback) `onCompleted` method is called when this happens. However, in certain cases the `onCancelled` method is called, which instead means that the gesture was aborted. By setting the `willContinue` flag, the emulated pointers will be hold down indefinitely also after the gesture completes. Moreover, a new gesture can continue to move such pointers with a new path. However, this is where things get tricky.

A gesture can only continue a previous one if all the following conditions apply:

- The previous gesture has at least one stroke with the `willContinue` flag set
- The new gesture contains all the strokes of the previous gesture, not more and not less
- The path of each individual stroke must start where the path of the corresponding stroke of the previous gesture ended (makes sense)

This means that, for example, it is not possible to add a new pointer on the screen without releasing all the other pointers belonging to the previous gesture. By doing so, the `onCancelled` method would be triggered. In practice this means that using the `AccessibilityService` to simulate multitouch apps it's not always possible, which is a pity due to the high potential of this API.

### Internals

The described behaviour has been deducted by trial and error since the official documentation does not cover it. Nevertheless, it can be confirmed by inspecting the Android source code.

The `AccessibilityService` is just a client for the [MotionEventInjector](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java) server where the actual events generation occurs. The `newGestureTriesToContinueOldOne` method performs a quick check to see if a pointer of the new gesture corresponds to a pointer of the previous one. The `prepareToContinueOldGesture` is the method which performs all the necessary checks to ensure that the new gesture can actually continue the previous one. Via `numContinuedStrokes` it checks that the number of strokes exactly matches the old ones and it is the reason why it's not possible to add new pointers to the previous gesture.

## InputManager

The `InputManger` is similar to the `Instrumentation` class as it allows to inject raw `InputEvents` to other apps provided that the application has the `INJECT_EVENTS` permission. Actually [the public API](https://developer.android.com/reference/android/hardware/input/InputManager) does not mention this ability. However, by looking [at its implementation](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/hardware/input/InputManager.java) we can see that it provides the `injectInputEvent` public method which performs the intended operation.

By using java reflection, it is possible to get a reference to this method and invoke it:

```
String methodName = "getInstance";
im = (InputManager) InputManager.class.getDeclaredMethod(methodName)
  .invoke(null);

methodName = "injectInputEvent";
injectInputEvent = InputManager.class.getMethod(methodName, InputEvent.class, Integer.TYPE);

// injectInputEvent.invoke(im, ...)
```

The interesting thing about `InputManager` is that by combining it with `adb` it is possible to bypass the `INJECT_EVENTS` permission! But how to run our custom program via adb? The `app_process` android utility comes to rescue. It allows to run a java program outside the Dalvik vm. Of course the program cannot use any of the android API which depends on linked libraries. `InputManager` just does the trick. To use it, it's necessary to create a `Main.cpp` class with the public `main` method. It can be even bundled in a normal APK along with the rest of the android app without any additional gradle configuration.

Once the APK is installed into the device, the main class can be invoked as follows (via `adb`):

```
export CLASSPATH=`pm path com.emanuelef.touchinjector`
app_process /data/local/tmp com.emanuelef.touchinjector.Main
```

This will start a new java process running the `Main` class where the `injectInputEvent` can be freely invoked. This process of course needs a way to communicate to the main app. A standard socket can be used for this.

Although this method is hacky and it relies on functions not part of the official API, which may change in the future, nevertheless it works and it represents the current state of art for system wide input injection on Android without root.