# TouchInjector

TouchInjector is an android app which simulates touch events by translating inputs from a remotely connected gamepad. The app does not require root.

TouchInjector is experimental and not user friendly. It is currently limited to the use case of controlling the Brawl Stars game from a linux PC with an Xbox 360 controller or the Nintendo Switch joycons.

Most settings are wired into the app and require changing its source code to be adapted. Pull requests to provide a gui are welcome. Please read the *Setup* section below carefully to properly set it up.

## How it works

Injecting input events to other apps is generally not allowed in android as it requires the [INJECT_EVENTS permission](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/res/AndroidManifest.xml). This permission can only be granted to apps signed with a system signature, which can be hard to accomplish even on rooted devices.

TouchInjector, on the other hand, takes advantage of the *adb* special permissions which allow it to inject events. By running a dedicated program via adb is ence possible to inject events and bypass the permission check.

## Setup

Before starting, it's necessary to satisfy the following requirements:

- A linux PC (e.g. raspberry) is needed to run the `touchinjector.py` companion script
- The android device is in debug mode and the [adb](https://developer.android.com/studio/command-line/adb) command is available on the PC
- The android device must stay connected to the PC in adb mode while the app is running (a USB connection is suggested)
- The gamepads must be connected and properly recognized by the PC (check with [jstest](https://jstest-gtk.gitlab.io/))
- The companion script currently only supports the Xbox 360 controller and the Nintendo Switch joycons. For the latter, it is necessary to install [hid-nintendo](https://github.com/nicman23/dkms-hid-nintendo) and [joycond](https://github.com/DanielOgorchock/joycond) to combine the joycons.
- Python dependencies: [python-evdev](https://github.com/gvalkov/python-evdev)

For the first run:

1. Start the Brawl Stars game and take the coordinates of all three sticks with their radius and optionally of the coords of the "pins" buttons. You can do this by taking a screenshot of the game controls and use an image editor to get the coords.
2. Edit `InputHandlerBS.java` and replace the coords in the `InputHandlerBS` contructor and in the `Pins` class with your own. For the sticks, you also need to specify the radius of the controls (move the stick on the screen to its farther location).
3. Build and install the modified android app
4. In the `touchinjector.py` script, enable the `DEBUG` flag. Then start the script and ensure that it detects your gamepad. You can now calibrate the `deadzone` of the `Xbox360Gamepad`/`JoyconsGamepad` classes. Once satisfied, turn the `DEBUG` flag off again.

Every app run requires the following steps:

1. Run the TouchInjector app and start the input service
2. If you have just connected the phone to the PC, run `adb forward tcp:7070 tcp:7070` to forward the TCP connection for the gamepad inputs via the USB cable. This provides minimal input lag and more reliability  compared to a WiFi connection.
3. Start the `touchinjector.py` script. It should connect to the android app via the TCP socket.
4. Execute the command `adb shell 'CLASSPATH=$(pm path com.emanuelef.touchinjector) app_process /data/local/tmp com.emanuelef.touchinjector.Main'`. It will start a process in the android device which listens for events from the android app and injects them thanks to the privileged adb position.
5. You can now use your gamepad to play Brawl Stars! You can check that everything works by opening the TouchInjector app and moving the joysticks. The simulated input positions will be drawn in the app view.

## Controls

```
  LSTICK -> control the movement stick
  RSTICK -> control the fire/special stick
      LT -> press to control the special stick instead of the fire stick
    B/RT -> press the fire/special stick
       A -> press the special stick
       Y -> press the gadget button
    HOME -> reset the inputs, useful if something gets stuck
DPAD/SEL -> show pins
```

## Additional Notes

Although it is possible to connect the Nintendo Switch joycons to an android device via bluetooth, the current driver only reports 8 positions for the joysticks, which makes them unusable for precision control. Nevertheless, `JoyconsIME.java` implements an [IME](https://developer.android.com/guide/topics/text/creating-input-method) to read the joycons input. It can be enabled from the `AndroidManifest.xml`. This IME, conflicts with the normal service TouchInjector uses, so be sure to stop the service via the app before enabling the IME.

## See Also

- [TouchMapper](https://github.com/Shyri/TouchMapper) - experimental input mapper, requires a MacOS PC.
- [KeyMapper](https://github.com/sds100/KeyMapper) - a multi-purpose input mapper.
- [Mantis](https://play.google.com/store/apps/details?id=app.mantispro.gamepad) - a complete software to gamepad input. Closed source.
