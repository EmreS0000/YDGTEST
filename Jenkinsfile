// ---------------- helpers (pipeline dışı) ----------------

def chromeArgsCommon() {
  return "--disable-features=HttpsOnlyMode,HttpsFirstModeV2,HttpsUpgrades;--ignore-certificate-errors;--allow-insecure-localhost;--no-first-run;--no-default-browser-check"
}

def e2eEnv(String chromeArgs) {
  return [
    "BASE_URL=http://app:8080",          // internal network alias
    "FRONTEND_URL=http://frontend",      // internal network alias (port 80)
    "SELENIUM_URL=http://localhost:4444/wd/hub",
    "CHROME_ARGS=${chromeArgs}"
  ]
}

def runE2E(String testName) {
  def chromeArgs = chromeArgsCommon()
  def envs = e2eEnv(chromeArgs)

  // DB args to force tests to use the Docker Postgres instead of H2
  def dbArgs = "-Dspring.datasource.url=jdbc:postgresql://localhost:5438/DogrulamaGecerleme " +
               "-Dspring.datasource.username=postgres " +
               "-Dspring.datasource.password=1234 " +
               "-Dspring.datasource.driver-class-name=org.postgresql.Driver " +
               "-Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"

  // Memory settings for the Test JVM
  def memoryArgs = "-Xmx1024m -XX:MaxMetaspaceSize=384m"

  withEnv(envs) {
    if (isUnix()) {
      sh "./mvnw ${env.MVN_ARGS} failsafe:integration-test failsafe:verify " +
         "${dbArgs} " +
         "-Dit.test=${testName} " +
         "-DargLine=\"-DBASE_URL=$BASE_URL -DFRONTEND_URL=$FRONTEND_URL -DSELENIUM_URL=$SELENIUM_URL -DCHROME_ARGS=$CHROME_ARGS ${memoryArgs}\""
    } else {
      bat ".\\mvnw.cmd %MVN_ARGS% failsafe:integration-test failsafe:verify " +
          "${dbArgs} " +
          "-Dit.test=\"${testName}\" " +
          "-DargLine=\"-DBASE_URL=%BASE_URL% -DFRONTEND_URL=%FRONTEND_URL% -DSELENIUM_URL=%SELENIUM_URL% -DCHROME_ARGS=%CHROME_ARGS% ${memoryArgs}\""
    }
  }
}

// ---------------- pipeline ----------------

pipeline {
  agent any

  options {
    timestamps()
    skipDefaultCheckout(true)
    disableConcurrentBuilds() // aynı anda 2 build docker’ı çarpıştırmasın
  }

  environment {
    COMPOSE_FILE = 'docker-compose.yml'
    COMPOSE_PROJECT_NAME = 'library-app'
    MVN_ARGS = '-U -B -Dfile.encoding=UTF-8'
    JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'

    // Build hızlandırma (opsiyonel ama iyi)
    DOCKER_BUILDKIT = '1'
    COMPOSE_DOCKER_CLI_BUILD = '1'
  }

  stages {

    stage('1-Checkout') {
      steps {
        deleteDir()
        checkout(changelog: false, poll: false, scm: [
          $class: 'GitSCM',
          branches: [[name: '*/main']],
          userRemoteConfigs: [[url: 'https://github.com/EmreS0000/YDGTEST.git']]
        ])
      }
    }

    stage('2-Build') {
      steps {
        script {
          if (isUnix()) sh "./mvnw ${env.MVN_ARGS} -DskipTests package"
          else          bat ".\\mvnw.cmd %MVN_ARGS% -DskipTests package"
        }
      }
    }

    stage('3-Unit Tests') {
      steps {
        script {
          if (isUnix()) sh "./mvnw ${env.MVN_ARGS} test -Dtest=!*IntegrationTest,!*SeleniumTest,!*E2E*"
          else          bat ".\\mvnw.cmd %MVN_ARGS% test -Dtest=!*IntegrationTest,!*SeleniumTest,!*E2E*"
        }
      }
      post {
        always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
      }
    }

    stage('4-Integration Tests') {
      steps {
        script {
          if (isUnix()) sh "./mvnw ${env.MVN_ARGS} test -Dtest=*IntegrationTest"
          else          bat ".\\mvnw.cmd %MVN_ARGS% test -Dtest=*IntegrationTest"
        }
      }
      post {
        always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml,target/failsafe-reports/*.xml' }
      }
    }

    stage('5-Run System on Docker') {
      steps {
        script {
          if (isUnix()) {
            // ✅ down yok: docker ayakta kalsın, cache bozulmasın
            sh "docker compose -p ${env.COMPOSE_PROJECT_NAME} -f ${env.COMPOSE_FILE} up -d --build"
            sh "docker compose -p ${env.COMPOSE_PROJECT_NAME} -f ${env.COMPOSE_FILE} ps"
          } else {
            bat "docker compose -p %COMPOSE_PROJECT_NAME% -f %COMPOSE_FILE% up -d --build"
            bat "docker compose -p %COMPOSE_PROJECT_NAME% -f %COMPOSE_FILE% ps"

            // ✅ wait script: Grid + Backend + Frontend hazır olana kadar bekle
            writeFile file: 'wait-ci.ps1', encoding: 'UTF-8', text: '''
$ErrorActionPreference = "SilentlyContinue"
$compose = $env:COMPOSE_FILE
$project = $env:COMPOSE_PROJECT_NAME

function Wait-Url([string]$url, [int]$tries, [int]$sleepSec) {
  for($i=0; $i -lt $tries; $i++){
    try {
      $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2
      if($r -and $r.StatusCode -ge 200 -and $r.StatusCode -lt 500){
        Write-Host ("OK: " + $url + " => " + $r.StatusCode)
        return $true
      }
    } catch {}
    Start-Sleep -Seconds $sleepSec
  }
  return $false
}

# 1) Selenium Grid ready
if(-not (Wait-Url "http://localhost:4444/status" 60 2)){
  Write-Host "FAIL: Grid not ready"
  & docker compose -p $project -f $compose logs --no-color selenium | Out-Host
  exit 1
}

# 2) Backend ready
# En sağlam endpoint: /actuator/health varsa onu kullan. Yoksa root bile yeterli.
$okApi = Wait-Url "http://localhost:8080/actuator/health" 300 2
if(-not $okApi){
  Write-Host "FAIL: Backend not reachable on 8080"
  & docker compose -p $project -f $compose logs --no-color app | Out-Host
  exit 1
}

# 3) Frontend ready
$okUi = Wait-Url "http://localhost:5173" 300 2
if(-not $okUi){
  Write-Host "FAIL: Frontend not reachable on 5173"
  & docker compose -p $project -f $compose logs --no-color frontend | Out-Host
  exit 1
}

Write-Host "READY: Selenium + Backend + Frontend"
exit 0
'''
            bat 'powershell -NoProfile -ExecutionPolicy Bypass -File wait-ci.ps1'
          }
        }
      }
    }

    stage('5.5-Smoke: selenium container reach host ports') {
      steps {
        script {
          if (!isUnix()) {
            writeFile file: 'smoke.ps1', encoding: 'UTF-8', text: '''
$ErrorActionPreference = "SilentlyContinue"
$compose = $env:COMPOSE_FILE
$project = $env:COMPOSE_PROJECT_NAME

function ExecSelenium([string]$innerCmd) {
  & docker compose -p $project -f $compose exec -T selenium sh -lc $innerCmd 2>&1
}

$api = "http://host.docker.internal:8080"
$ui  = "http://host.docker.internal:5173"

Write-Host "[smoke] selenium -> backend"
$out = ExecSelenium "curl -sS -I $api || true"
$out | ForEach-Object { Write-Host $_ }

Write-Host "[smoke] selenium -> frontend"
$out2 = ExecSelenium "curl -sS -I $ui || true"
$out2 | ForEach-Object { Write-Host $_ }

exit 0
'''
            bat 'powershell -NoProfile -ExecutionPolicy Bypass -File smoke.ps1'
          }
        }
      }
    }

    // ---------------- 6) En az 3 senaryo (ayrı stage) ----------------

    stage('6.1-Selenium Scenario 1: Login') {
      steps { script { runE2E("LoginSeleniumTest") } }
      post { always { junit allowEmptyResults: true, testResults: 'target/failsafe-reports/*.xml' } }
    }

    stage('6.2-Selenium Scenario 2: Register') {
      steps { script { runE2E("RegisterSeleniumTest") } }
      post { always { junit allowEmptyResults: true, testResults: 'target/failsafe-reports/*.xml' } }
    }

    stage('6.3-Selenium Scenario 3: Book Management') {
      steps { script { runE2E("BookManagementSeleniumTest") } }
      post { always { junit allowEmptyResults: true, testResults: 'target/failsafe-reports/*.xml' } }
    }
  }

  post {
    always {
      // ✅ Docker AYAKTA KALSIN: sadece log/ps al
      script {
        if (isUnix()) {
          sh "docker compose -p ${env.COMPOSE_PROJECT_NAME} -f ${env.COMPOSE_FILE} ps || true"
          sh "docker compose -p ${env.COMPOSE_PROJECT_NAME} -f ${env.COMPOSE_FILE} logs --no-color --tail=120 > docker-logs.txt || true"
        } else {
          bat "docker compose -p %COMPOSE_PROJECT_NAME% -f %COMPOSE_FILE% ps"
          bat "docker compose -p %COMPOSE_PROJECT_NAME% -f %COMPOSE_FILE% logs --no-color --tail=120 > docker-logs.txt"
        }
      }
      archiveArtifacts artifacts: 'target/failsafe-reports/*,target/surefire-reports/*,docker-logs.txt', allowEmptyArchive: true
    }
  }
}