pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Test - Print Info') {
      steps {
        sh '''
          echo "=== Jenkins Trigger Test ==="
          echo "Time: $(date -u)"
          echo "Workspace: $(pwd)"
          echo "Job: $JOB_NAME"
          echo "Build: #$BUILD_NUMBER"
          echo "Branch: ${BRANCH_NAME:-unknown}"
          echo "Commit:"
          git rev-parse HEAD || true
          echo "Last 5 commits:"
          git --no-pager log -5 --oneline || true
          echo "Repo files (top):"
          ls -al | head -n 50
          echo "=== End ==="
        '''
      }
    }
  }

  post {
    always {
      echo "Pipeline finished."
    }
  }
}
