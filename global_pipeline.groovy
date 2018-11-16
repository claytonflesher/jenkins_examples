def call() {
    pipeline {
        agent none
        environment {
            DEPLOY_CREDS = credentials('anypoint_platform_creds')
            SLACK_TOKEN  = credentials('slack_token')
        }
        stages {
            stage('Build without tests') {
                agent {
                    docker {
                        image 'maven:3-alpine'
                        args '-v /root/.m2:/root/.m2'
                    }
                }
                steps {
                    sh 'mvn clean build -DskipTests'
                }
            }
            stage('Build with tests') {
                agent {
                    docker {
                        image 'maven:3-alpine'
                        args '-v /root/.m2:/root/.m2'
                    }
                }
                steps {
                    sh 'mvn clean build'
                    stash name: 'application', includes: 'target/*'
                }
            }
            stage('Deploy to artifact repository') {
                agent {
                    docker {
                        image 'maven:3-alpine'
                        args '-v /root/.m2:/root/.m2'
                    }
                }
                steps {
                    unstash 'application'
                    sh 'mvn clean deploy'
                    stash name: 'application', includes: 'target/*'
                }
            }
            stage('Deploy to Development environment') {
                agent {
                    docker {
                        image 'hoeggsoftware/anypoint-toolbox'
                    }
                }
                steps {
                    unstash 'application'
                    sh 'anypoint-cli --username=$DEPLOY_CREDS_USR --password=$DEPLOY_CREDS_PSW --environment=DEV runtime-mgr cloudhub-application deploy'
                    stash name: 'application', includes: 'target/*'
                }
            }
            stage('QA Input') {
                agent none
                steps {
                    input message: 'Deploy to QA?', submitter: 'AD_QA_Submitter_Group'
                }
            }
            stage('Deploy to QA') {
                agent {
                    docker 'hoeggsoftware/anypoint-toolbox'
                }
                unstash 'application'
                sh 'anypoint-cli --username=$DEPLOY_CREDS_USR --password=$DEPLOY_CREDS_PSW --environment=QA runtime-mgr cloudhub-application deploy'
                stash name: 'application', includes: 'target/*'
            }
            stage('PROD Input') {
                agent none
                steps {
                    input message: 'Deploy to Production?', submitter: 'AD_PROD_Submitter_Group'
                }
            }
            stage('Deploy to Production') {
                agent {
                    docker 'hoeggsoftware/anypoint-toolbox'
                }
                unstash 'application'
                sh 'anypoint-cli --username=$DEPLOY_CREDS_USR --password=$DEPLOY_CREDS_PSW --environment=PROD runtime-mgr cloudhub-application deploy'
            }
        }
        post {
            always {
                deleteDir()
            }
            success {
                slackSend channel: '#jenkins-latest', color: 'green', message: 'Success: ${currentBuild.fullDisplayName}', teamDomain: 'example', token: 'token'

            }
            failure {
                slackSend channel: '#jenkins-latest', color: 'red', message: 'Success: ${currentBuild.fullDisplayName}', teamDomain: 'example', token: 'token'
            }
        }
    }
}
