import glob, os, shutil

# Define paths
src = './patch/'
classes_dst = './ExoPlayer/demos/main/src/main/java/com/google/android/exoplayer2/demo/'
manifest_dst = './ExoPlayer/demos/main/src/main/'
gradle_dst = './Exoplayer/demos/main/'
strings_dst = './Exoplayer/demos/main/src/main/res/values/'

# Identify classes and copy them in the right location
class_files = glob.iglob(os.path.join(src, "*.java"))
for file in class_files:
    if os.path.isfile(file):
        shutil.copy2(file, classes_dst)
        print("Class  " + file + "  successfully copied into Exoplayer project")

# Overwrite the Android manifest
file = './patch/AndroidManifest.xml'
shutil.copy2(file, manifest_dst)
print("Android manifest successfully copied into Exoplayer project")

# Overwrite the build.gradle file
file = './patch/build.gradle'
shutil.copy2(file, gradle_dst)
print("build.gradle successfully copied into Exoplayer project")

# Overwrite the strings.xml file
file = './patch/strings.xml'
shutil.copy2(file, strings_dst)
print("strings.xml successfully copied into Exoplayer project")