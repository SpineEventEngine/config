@echo off

rem
rem The script to update the project configuration files.
rem 
rem The code of the script assumes that:
rem   1. The project uses this code as a Git sub-module installed in the `config`
rem      directory under the project root.
rem   2. The script is called from the root of the project.
rem

rem Update the module with reference to the latest version.
rem In this state there is no current branch.
git submodule update

rem Make the `config` dir current.
cd config

rem Set the current branch to master.
git checkout master

echo "Pulling changes from remote repo"
git pull

echo "Updating IDEA configuration"
xcopy /S/E/I/F/Y .idea ..\.idea

echo "Updating CI config files"
xcopy /F/Y .codacy.yaml ..\
xcopy /F/Y .codecov.yml ..\
xcopy /F/Y .gitattributes ..\

rem Copy `.gitignore` only if it's not yet created.
IF NOT EXIST "..\.gitignore" (
	echo "Creating .gitignore"
	xcopy /F/Y .gitignore ..\
)

rem Copy `version.gradle` only if it is not yet created in the project.
IF NOT EXIST "..\version.gradle" (
	echo "Creating version.gradle
	xcopy /F/Y version.gradle ..\
)

cd ..

echo "Done."
