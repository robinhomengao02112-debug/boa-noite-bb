' Inicia o CONTROLADOR da live escondido (sem janela preta).
' Usado pelo atalho da Inicializacao do Windows e pelo atalho "Iniciar controlador".
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(fso.GetParentFolderName(WScript.ScriptFullName))
If Not fso.FolderExists(dir & "\data") Then fso.CreateFolder(dir & "\data")
sh.CurrentDirectory = dir
sh.Run "cmd /c node src\controller.js >> data\console.log 2>&1", 0, False
