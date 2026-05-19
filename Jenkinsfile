pipeline {
  agent any

  environment {
    DEPLOY_HOST = "10.0.0.16"
    DEPLOY_CRED = "deployInstance"

    REMOTE_UPLOAD_DIR = "/home/deploy/release/uploads"
    REMOTE_JAR_NAME = "fixlog.jar"
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build Jar') {
      steps {
        sh '''
          set -euo pipefail
          chmod +x ./gradlew
          ./gradlew clean build -x test
          echo "Artifacts:"
          ls -al build/libs
        '''
      }
    }

    stage('Upload bootJar as fixlog.jar') {
      steps {
        sshagent(credentials: [env.DEPLOY_CRED]) {
          sh '''
            set -euo pipefail

            BOOT_JAR=$(ls -1 build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)
            echo "Selected: $BOOT_JAR"

            cp "$BOOT_JAR" ./fixlog.jar

            ssh -o StrictHostKeyChecking=no deploy@${DEPLOY_HOST} "mkdir -p ${REMOTE_UPLOAD_DIR}"
            scp -o StrictHostKeyChecking=no ./fixlog.jar deploy@${DEPLOY_HOST}:${REMOTE_UPLOAD_DIR}/${REMOTE_JAR_NAME}

            echo "✅ Uploaded: ${REMOTE_UPLOAD_DIR}/${REMOTE_JAR_NAME}"
          '''
        }
      }
    }

    stage('Restart fixlog (script)') {
      steps {
        sshagent(credentials: [env.DEPLOY_CRED]) {
          sh '''
            set -euo pipefail
            ssh -o StrictHostKeyChecking=no deploy@${DEPLOY_HOST} "/home/deploy/release/run_fixlog.sh"
          '''
        }
      }
    }
  }
}
