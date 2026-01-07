Inspired by Wes Bos' Sticker Dream (https://github.com/wesbos/sticker-dream) I wanted to give my nieces and nephews an AI powered sticker printer for Christmas, but having it run a separate webserver was going to be too much for them and my siblings. I have a bunch of old Android phones kicking around and it would be good to repurpose them, so I built a bluetooth-connected sticker printing app. It turned out to be a bigger hit than I expected so I'm pushing up the code so folks can do it themselves.

What you'll need:
- An  Android phone with a microphone. The oldest version of Android I tested on was API level 25 but in theory this should run on any device which supports Jetpack. 
- A thermal shipping label printer than supports mode 0 TSPL. I used Bos' suggested Phomemo PM-241BT; this is the only one I know works with this code. I think some of the protocol is unique to Phomemo but I am not sure exactly what.
- A working Android development setup (https://developer.android.com/)

Steps to get it up and running:
- Clone the repo
- Get a Gemini API key from Google AI Studio (https://aistudio.google.com/app/apikey) and put it in local.properties
  - ```google_api_key=<your key here>```
- Connect the phone to printer via BT. It will not hold a connection open as with headphones. 
- Run the app on your device
- Follow the onscreen instructions to print.

Locking down the device:
I didn't want to give young children unfettered access to the internet so I locked the phone to the app in the following way:
- Putting a build of LineageOS (https://lineageos.org/) on the device. This usually has the bonus of allowing more modern versions of Android to run on the device 
- Running ```adb shell dpm set-device-owner com.codestersunion.stickerprinter/.AdminReceiver``` so that the app is immediately launched on boot
- You shoudl be able to access bluetooth and wifi settings via the settings screen inside the app.

This locks the device to this particular app though not irrevocably. My threat model does not include my nieces and nephews being able to engineer their way out of it; you may wish to take extra precautions.

Low-hanging fruit for improvement:
- Better UI - I vibe-coded a lot of this so the UI needs some polishing
- Whisper on device - currently voice is translated to text through a call to the Gemini model - it would be cheaper and more efficient to run a local STT.
- A 3D printed holster for the phone to attach it to the printer.
- Better prompting - the model almost always returns something in a particular style, it could be more flexible
- Better alignment of images to paper size - right now a lot of whitespace is left on the page