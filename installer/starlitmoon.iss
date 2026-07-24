; StarlitMoon Launcher installer
; Default install: %AppData%\Roaming\StarlitMoonLauncher (user-selectable)

#define AppName "StarlitMoon Launcher"
#define AppVersion "1.3.3"
#define AppPublisher "StarlitMoon"
#define AppURL "https://starlit-moon.ru"
#define AppExeName "StarlitMoonLauncher.exe"
#define AppId "{{8F4A2C91-6B7E-4D11-9A3F-2E8C1B0D5477}"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DefaultDirName={userappdata}\StarlitMoonLauncher
DefaultGroupName=StarlitMoon
DisableProgramGroupPage=yes
DisableDirPage=no
AlwaysShowDirOnReadyPage=yes
UsePreviousAppDir=yes
AllowNoIcons=yes
OutputDir=..\dist\v1.3.3
OutputBaseFilename=StarlitMoonLauncher-Setup-1.3.3
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=dark
WizardSizePercent=120
; Иконка как на сайте (favicon: луна + звезда)
SetupIconFile=assets\icon.ico
WizardSmallImageFile=assets\icon.png
WizardSmallImageBackColor=$07090F
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName}
CloseApplications=yes
DirExistsWarning=no
ShowLanguageDialog=no
VersionInfoVersion={#AppVersion}.0
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} Setup
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}

; Старые jar копятся при обновлении и могли подхватывать траву Minecraft (1.0.7/1.0.8).
[InstallDelete]
Type: files; Name: "{app}\app\starlitmoon-launcher-*.jar"
Type: files; Name: "{app}\app\StarlitMoonLauncher.cfg"
Type: files; Name: "{app}\{#AppExeName}"
Type: files; Name: "{userdesktop}\{#AppName}.lnk"
Type: files; Name: "{group}\{#AppName}.lnk"

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Messages]
russian.BeveledLabel=StarlitMoon
russian.WelcomeLabel1=Добро пожаловать в [name]
russian.WelcomeLabel2=Этот мастер установит [name/ver] на ваш компьютер.%n%nЛаунчер Minecraft-сервера StarlitMoon — вход, кабинет и запуск клиента в одном месте.%n%nПо умолчанию установка идёт в AppData\Roaming. Вы сможете выбрать другой путь на следующем шаге.%n%nРекомендуется закрыть другие приложения перед продолжением.
russian.SelectDirLabel3=Мастер установит [name] в следующую папку.
russian.SelectDirBrowseLabel=Чтобы установить в другое место, нажмите «Обзор».
russian.FinishedHeadingLabel=Установка завершена
russian.FinishedLabelNoIcons=[name] успешно установлен.%n%nМожно запускать лаунчер и играть на StarlitMoon.
russian.ClickFinish=Нажмите «Готово», чтобы закрыть мастер.
russian.SelectTasksLabel2=Выберите дополнительные параметры, затем нажмите «Далее».

[CustomMessages]
ModePageTitle=Действие
ModePageDesc=Что сделать с лаунчером?
ModePageCaption=Выберите один вариант:
ModeInstall=Установить — новая установка
ModeUpdate=Обновить — заменить файлы в выбранной папке
ModeRemove=Удалить — снять лаунчер с компьютера
TaskDesktop=Ярлык на рабочем столе
TaskAdditional=Дополнительно:
ConfirmRemove=Удалить StarlitMoon Launcher?
RemovedOk=Лаунчер удалён.
NotFound=Установленная копия не найдена.
WillFreshInstall=Установка не найдена. Будет выполнена новая установка.
DefaultHint=По умолчанию: %AppData%\Roaming\StarlitMoonLauncher

[Tasks]
Name: "desktopicon"; Description: "{cm:TaskDesktop}"; GroupDescription: "{cm:TaskAdditional}"; Flags: checkedonce

[Files]
Source: "..\build\compose\binaries\main-release\app\StarlitMoonLauncher\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Check: ShouldInstallFiles

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; IconFilename: "{app}\{#AppExeName}"; AppUserModelID: "StarlitMoon.Launcher"; Check: ShouldInstallFiles
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; IconFilename: "{app}\{#AppExeName}"; AppUserModelID: "StarlitMoon.Launcher"; Tasks: desktopicon; Check: ShouldInstallFiles

[Run]
Filename: "{sys}\ie4uinit.exe"; Parameters: "-show"; StatusMsg: "Обновление иконок Windows…"; Flags: runhidden skipifdoesntexist; Check: ShouldInstallFiles
Filename: "{app}\{#AppExeName}"; Description: "Запустить {#AppName}"; Flags: nowait postinstall skipifsilent; Check: ShouldInstallFiles

[Code]
var
  ModePage: TInputOptionWizardPage;
  HintLabel: TNewStaticText;

function UninstallRegKey: String;
begin
  Result := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{8F4A2C91-6B7E-4D11-9A3F-2E8C1B0D5477}_is1';
end;

function GetInstalledDir: String;
begin
  Result := '';
  if not RegQueryStringValue(HKCU, UninstallRegKey, 'InstallLocation', Result) then
    if not RegQueryStringValue(HKCU, UninstallRegKey, 'Inno Setup: App Path', Result) then
      Result := '';
  if (Result <> '') and (Result[Length(Result)] = '\') then
    Delete(Result, Length(Result), 1);
end;

function GetUninstallerPath: String;
var
  S: String;
begin
  Result := '';
  if RegQueryStringValue(HKCU, UninstallRegKey, 'UninstallString', S) then
    Result := RemoveQuotes(S);
end;

function SelectedMode: Integer;
begin
  if ModePage.Values[0] then Result := 0
  else if ModePage.Values[1] then Result := 1
  else Result := 2;
end;

function IsAppInstalled: Boolean;
var
  Dir: String;
begin
  Dir := GetInstalledDir;
  if Dir <> '' then
    Result := FileExists(Dir + '\{#AppExeName}')
  else
    Result :=
      FileExists(ExpandConstant('{userappdata}\StarlitMoonLauncher\{#AppExeName}')) or
      FileExists(ExpandConstant('{localappdata}\StarlitMoonLauncher\{#AppExeName}'));
end;

function ShouldInstallFiles: Boolean;
begin
  { Silent auto-update must always replace files (never treat as uninstall). }
  if WizardSilent then
  begin
    Result := True;
    Exit;
  end;
  Result := SelectedMode < 2;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if (PageID = wpSelectDir) or (PageID = wpSelectTasks) then
    Result := SelectedMode = 2;
end;

procedure ApplyDirForMode;
var
  Prev: String;
begin
  Prev := GetInstalledDir;
  if (SelectedMode = 1) and (Prev <> '') then
    WizardForm.DirEdit.Text := Prev
  else if Trim(WizardForm.DirEdit.Text) = '' then
    WizardForm.DirEdit.Text := ExpandConstant('{userappdata}\StarlitMoonLauncher');
end;

procedure InitializeWizard;
begin
  WizardForm.WelcomeLabel1.Font.Style := [fsBold];
  WizardForm.WelcomeLabel1.Font.Size := 12;
  WizardForm.PageNameLabel.Font.Style := [fsBold];

  ModePage := CreateInputOptionPage(wpWelcome,
    ExpandConstant('{cm:ModePageTitle}'),
    ExpandConstant('{cm:ModePageDesc}'),
    ExpandConstant('{cm:ModePageCaption}'),
    True, False);
  ModePage.Add(ExpandConstant('{cm:ModeInstall}'));
  ModePage.Add(ExpandConstant('{cm:ModeUpdate}'));
  ModePage.Add(ExpandConstant('{cm:ModeRemove}'));

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

  HintLabel := TNewStaticText.Create(WizardForm);
  HintLabel.Parent := WizardForm.SelectDirPage;
  HintLabel.Caption := ExpandConstant('{cm:DefaultHint}');
  HintLabel.Left := WizardForm.DirEdit.Left;
  HintLabel.Top := WizardForm.DirBrowseButton.Top + WizardForm.DirBrowseButton.Height + ScaleY(12);
  HintLabel.Width := WizardForm.SelectDirPage.ClientWidth - ScaleX(24);
  HintLabel.AutoSize := False;
  HintLabel.WordWrap := True;
  HintLabel.Font.Color := $C4C9D8;
  HintLabel.Font.Size := 9;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Uninstaller: String;
  ResultCode: Integer;
  Prev: String;
begin
  Result := True;
  if CurPageID = ModePage.ID then
  begin
    if SelectedMode = 2 then
    begin
      Uninstaller := GetUninstallerPath;
      if (Uninstaller = '') or (not FileExists(Uninstaller)) then
      begin
        Prev := GetInstalledDir;
        if Prev <> '' then
          Uninstaller := Prev + '\unins000.exe';
        if (Uninstaller = '') or (not FileExists(Uninstaller)) then
        begin
          if FileExists(ExpandConstant('{userappdata}\StarlitMoonLauncher\unins000.exe')) then
            Uninstaller := ExpandConstant('{userappdata}\StarlitMoonLauncher\unins000.exe')
          else if FileExists(ExpandConstant('{localappdata}\StarlitMoonLauncher\unins000.exe')) then
            Uninstaller := ExpandConstant('{localappdata}\StarlitMoonLauncher\unins000.exe');
        end;
      end;

      if (Uninstaller <> '') and FileExists(Uninstaller) then
      begin
        if MsgBox(ExpandConstant('{cm:ConfirmRemove}'), mbConfirmation, MB_YESNO) = IDYES then
        begin
          Exec(Uninstaller, '/SILENT', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
          MsgBox(ExpandConstant('{cm:RemovedOk}'), mbInformation, MB_OK);
        end;
      end
      else
        MsgBox(ExpandConstant('{cm:NotFound}'), mbError, MB_OK);
      Result := False;
      WizardForm.Close;
    end
    else if (SelectedMode = 1) and (not IsAppInstalled) then
    begin
      MsgBox(ExpandConstant('{cm:WillFreshInstall}'), mbInformation, MB_OK);
      ModePage.Values[0] := True;
      ModePage.Values[1] := False;
      ModePage.Values[2] := False;
      ApplyDirForMode;
    end
    else
      ApplyDirForMode;
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpSelectDir then
  begin
    if Trim(WizardForm.DirEdit.Text) = '' then
      WizardForm.DirEdit.Text := ExpandConstant('{userappdata}\StarlitMoonLauncher');
    if SelectedMode = 1 then
      ApplyDirForMode;
  end;
end;
