#define MyAppName "VFAB AI Creator Studio"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "VFAB AI"

[Setup]
AppId={{D7A4C980-6E9B-4B42-9D71-7F1A0E2E36C4}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\VFAB AI
DefaultGroupName=VFAB AI Creator Studio
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir=..\dist\installer
OutputBaseFilename=VFAB-AI-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\VFABAI.exe
CloseApplications=no
RestartApplications=no

[Files]
Source: "..\dist\VFABAI\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Icons]
Name: "{group}\VFAB AI Creator Studio"; Filename: "{cmd}"; Parameters: "/c ""{app}\Start-VFAB-AI.bat"""; WorkingDir: "{app}"
Name: "{autodesktop}\VFAB AI Creator Studio"; Filename: "{cmd}"; Parameters: "/c ""{app}\Start-VFAB-AI.bat"""; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{cmd}"; Parameters: "/c ""{app}\Start-VFAB-AI.bat"""; WorkingDir: "{app}"; Description: "Launch VFAB AI"; Flags: postinstall nowait skipifsilent
