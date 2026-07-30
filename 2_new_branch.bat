@echo off
setlocal enabledelayedexpansion
chcp 65001 > nul
title Git Branch Selector

:: ==========================================
:: [설정] 팀장 전용 비밀번호 & 메인 브랜치 20270729
:: ==========================================
set ADMIN_PW=1234
set MAIN_BRANCH=main

echo ==========================================
echo        2. Select & Switch Branch
echo ==========================================
echo  본인의 역할을 선택하세요:
echo.
echo    [1] 팀장   (김호경) - ※ 비밀번호 필요
echo    [2] 팀원1  (임태욱)
echo    [3] 팀원2  (조성한)
echo    [4] 팀원3  (이영준)
echo ==========================================

set /p CHOICE="번호를 입력하세요 (1-4): "

:: 팀장 선택 시 비밀번호 검사d
if "%CHOICE%"=="1" (
    echo.
    set /p INPUT_PW="팀장 비밀번호를 입력하세요: "
    
    :: !INPUT_PW!로 비교해야 사용자가 새로 입력한 값을 정확히 인식합니다.
    if not "!INPUT_PW!"=="%ADMIN_PW%" (
        echo.
        echo [접근 거부] 비밀번호가 틀렸습니다! 팀원 전용 번호를 선택해 주세요.
        pause
        exit /b
    )
    set TARGET_BRANCH=leader
)

if "%CHOICE%"=="2" set TARGET_BRANCH=member1
if "%CHOICE%"=="3" set TARGET_BRANCH=member2
if "%CHOICE%"=="4" set TARGET_BRANCH=member3

if "%TARGET_BRANCH%"=="" (
    echo [오류] 잘못된 선택입니다. 스크립트를 종료합니다.
    pause
    exit /b
)

echo.
echo 선택된 브랜치: [%TARGET_BRANCH%]
echo.

echo [1/3] 기준 브랜치(%MAIN_BRANCH%) 최신화 중...
git checkout %MAIN_BRANCH%
git pull origin %MAIN_BRANCH%

echo.
echo [2/3] '%TARGET_BRANCH%' 브랜치 확인 및 이동...
git show-ref --verify --quiet refs/heads/%TARGET_BRANCH%
if %errorlevel% equ 0 (
    git checkout %TARGET_BRANCH%
    git pull origin %TARGET_BRANCH%
) else (
    git checkout -b %TARGET_BRANCH%
)

echo.
echo ==========================================
echo [완료] 현재 브랜치: [%TARGET_BRANCH%]
echo ==========================================
pause