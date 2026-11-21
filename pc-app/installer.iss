; Instalador do Buds Control PC (Inno Setup 6)
#define AppName "Buds Control PC"
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
AppId={{6B7F2C1E-4D8A-4E5B-9C3F-B0D5C0A11E42}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Buds Control
DefaultDirName={localappdata}\Programs\BudsControlPC
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=out
OutputBaseFilename=BudsControlPC-Setup
SetupIconFile=BudsControlPC\app.ico
UninstallDisplayIcon={app}\BudsControlPC.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes

[Languages]
Name: "pt"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Tasks]
Name: "desktopicon"; Description: "Criar atalho na Área de Trabalho"; GroupDescription: "Atalhos:"
Name: "autostart"; Description: "Iniciar junto com o Windows (na bandeja)"; GroupDescription: "Opções:"; Flags: unchecked

[Files]
Source: "publish\BudsControlPC.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\BudsControlPC.exe"
Name: "{group}\Desinstalar {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\BudsControlPC.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "BudsControlPC"; ValueData: """{app}\BudsControlPC.exe"" --minimized"; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{app}\BudsControlPC.exe"; Description: "Abrir o {#AppName} agora"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "taskkill"; Parameters: "/IM BudsControlPC.exe /F"; Flags: runhidden; RunOnceId: "KillApp"
