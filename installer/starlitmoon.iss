; StarlitMoon Launcher installer

#define AppName "StarlitMoon Launcher"
#define AppVersion "1.0.3"
#define AppPublisher "StarlitMoon"
#define AppExeName "StarlitMoonLauncher.exe"
#define AppId "{{8F4A2C91-6B7E-4D11-9A3F-2E8C1B0D5477}"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\StarlitMoonLauncher
DefaultGroupName=StarlitMoon
DisableProgramGroupPage=yes
OutputDir=..\dist\v1.0.3
OutputBaseFilename=StarlitMoonLauncher-Setup-1.0.3
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#AppExeName}
CloseApplications=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "Ярлык на рабочем столе"; GroupDescription: "Дополнительно:"; Flags: unchecked

[Files]
Source: "..\build\compose\binaries\main-release\app\StarlitMoonLauncher\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: ShouldInstallFiles

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Check: ShouldInstallFiles
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon; Check: ShouldInstallFiles

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Запустить {#AppName}"; Flags: nowait postinstall skipifsilent; Check: ShouldInstallFiles

[Code]
var
  ModePage: TInputOptionWizardPage;

function SelectedMode: Integer;
begin
  if ModePage.Values[0] then Result := 0
  else if ModePage.Values[1] then Result := 1
  else Result := 2;
end;

function IsAppInstalled: Boolean;
begin
  Result := FileExists(ExpandConstant('{localappdata}\StarlitMoonLauncher\{#AppExeName}'));
end;

function ShouldInstallFiles: Boolean;
begin
  Result := SelectedMode < 2;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if (PageID = wpSelectDir) or (PageID = wpSelectTasks) then
    Result := SelectedMode = 2;
end;

procedure InitializeWizard;
begin
  ModePage := CreateInputOptionPage(wpWelcome,
    'Действие', 'Что сделать с лаунчером?',
    'Выберите один вариант:', True, False);
  ModePage.Add('Установить');
  ModePage.Add('Обновить');
  ModePage.Add('Удалить');

  if IsAppInstalled then
  begin
    ModePage.Values[0] := False;
    ModePage.Values[1] := True;
    ModePage.Values[2] := False;
  end
  else
  begin
    ModePage.Values[0] := True;
    ModePage.Values[1] := False;
    ModePage.Values[2] := False;
  end;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Uninstaller: String;
  ResultCode: Integer;
begin
  Result := True;
  if CurPageID = ModePage.ID then
  begin
    if SelectedMode = 2 then
    begin
      Uninstaller := ExpandConstant('{localappdata}\StarlitMoonLauncher\unins000.exe');
      if FileExists(Uninstaller) then
      begin
        if MsgBox('Удалить StarlitMoon Launcher?', mbConfirmation, MB_YESNO) = IDYES then
        begin
          Exec(Uninstaller, '/SILENT', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
          MsgBox('Лаунчер удалён.', mbInformation, MB_OK);
        end;
      end
      else
        MsgBox('Установленная копия не найдена.', mbError, MB_OK);
      Result := False;
      WizardForm.Close;
    end
    else if (SelectedMode = 1) and (not IsAppInstalled) then
    begin
      MsgBox('Установка не найдена. Будет выполнена новая установка.', mbInformation, MB_OK);
      ModePage.Values[0] := True;
      ModePage.Values[1] := False;
      ModePage.Values[2] := False;
    end;
  end;
end;
