import os
import strutils

proc readData(name:string):string=
    var 
        done=false
    echo "Please enter ",name
    while not done:
        var s=readLine(stdin)
        if s == "":
            echo "Please enter a value"
        else:
            done=true
            result=s 
        


proc getInfo(test=false):(string,string)=
    if test:
        result=("GuyiTest","com.lint.guyitest")
    else:
        var 
            name=readData("App Name eg GuyiTest")
            id=readData("App id eg com.lint.guyi")
        
        result=(name,id)
proc copyTempFolder(name:string):bool=
    try:
        os.copyDir("./temp/guyiandroid","./dist/androidapp")
        result=true
    except:
        echo "could not copy ",getCurrentException().msg
proc renameFields(name:string,id:string):bool=
    try:
        var 
            man_path = "./dist/androidapp/app/src/main/AndroidManifest.xml"
            man_data = readFile(man_path)
            bgradle_path = "./dist/androidapp/app/build.gradle"
            bgradle_data = readFile(bgradle_path)
        man_data  = man_data.replace("@string/app_name",name)
        writeFile(man_path,man_data)
        bgradle_data = bgradle_data.replace("applicationId \"com.example.guyiandroid\"","applicationId \"" & id & "\"")
        writeFile(bgradle_path,bgradle_data)
        result = true
    except:
        echo "Error android files modifing manifest",getCurrentException().msg
proc copy_icon()=
    try:
        var 
            dest_path =    "./dist/androidapp/app/src/main/res/mipmap/ic_launcher.png"
            src_path = "./android_icon.png"
        if fileExists(src_path):
            copyFile(src_path,dest_path)     
        else:
            if fileExists(dest_path):removeFile(dest_path)
            echo "No android_icon.png found\n"
    except:
        echo "Copying icon failed"
proc copyAppFile()=
    try:
        var 
            app_dest = "./dist/androidapp/app/src/main/assets/app.guyi"
            app_path = "./app.guyi"
        if fileExists(app_path):
            copyFile(app_path,app_dest)
        else:
            if fileExists(app_dest):removeFile(app_dest)
            echo "no app.guyi file found (you can add it manually to the assets folder)"
    except:
        echo "Failed to copy app.guyi file", getCurrentException().msg

proc terminateApp(msg:string)=
    echo "Guyi project generation failed ",msg

proc createApp*() : void = 
    var (name,id)=getInfo(true)
    echo name," ",id
    copyAppFile()
    if not copyTempFolder(name):terminateApp("copying template failed")
    if not renameFields(name,id):terminateApp("Modify template files failed")
    copy_icon()




