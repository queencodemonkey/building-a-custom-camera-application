AnDevCon 2014: "Building a Custom Camera Software"
=============================

This repository contains the Android project that I used for my AnDevCon Boston 2014 Presentation "Building a Custom Camera Application." I actually plan to clean-up the code and package some of the code in a library for re-use so please drop me a note if you are interested in that or any other features which I did not get a chance to discuss.

Project structure
-----------------

So for demonstration purposes and for playing-around-with-gradle purposes, I created a different product flavor for each demo application. There are some flavors with minute changes and some which I did not actually use or both. For those demos that I actually reviewed today, you can install them via gradle as follow:

1. Basic setup and picture taking: demonstration of camera setup, calling the `Camera#takePicture` method and displaying image data in an `ImageView`.
  - Flavor: basicPictureTaking
  - Main activity: `BasicPictureTakingActivity.java`
  - Build command: `./gradlew iBPTD`
2. Using `CameraInfo` to switch cameras: demonstration of how to query `CameraInfo` for the various cameras on a device and using info to find and switch between device cameras.
  - Flavor: basicCameraInfo
  - Main activity: `BasicCameraInfoActivity.java`
  - Build command: `./gradlew iBCID`
3. Dealing with activity orientation vs. camera/preview orientation.
  - Flavor: basicOrientation
  - Main activity: `BasicOrientationActivity.java`
  - Build command: `./gradlew iBOD`
4. Using `Camera.Parameters` to access device settings.
  - Flavor: basicParameters
  - Main activity: `BasicParametersActivity.java`
  - Build command: `./gradlew iBPD`
5. Implementing touch-to-focus with focus areas and `Camera.Area`.
  - Flavor: area
  - Main activity: `CameraAreaActivity.java`
  - Build command: `./gradlew iAD`
6. Using face detection.
  - Flavor: faceDetection
  - Main activity: `FaceDetectionActivity.java`
  - Build command: `./gradlew iFDD`

Other classes
-------------
- `PreviewSurface`: A `SurfaceView` class used in `BasicParametersActivity` and `BasicOrientationActivity` to consolidate `SurfaceView`-related logic.
- `Preview` and `PreviewOverlay`: Views used in `CameraAreaActivity` and `FaceDetectionActivity` in lieu of a plain `SurfaceView` or `PreviewSurface`. Includes logic for capturing touch events and drawing on top of a camera preview.
