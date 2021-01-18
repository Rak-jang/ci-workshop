pipeline {
    agent any
    environment {
        // แก้ไขเป็นของผู่เรียน
        USERID = 'demo'
        USERGITHUB = 'peerapach'
        // ไม่ต้องแก้ไข
        PROJECTID = 'fluid-analogy-267415'
        CLUSTERNAME = 'cluster-1'
        CLUSTERLOCATION = 'asia-southeast1-c'
        CREDENTIALS_ID = 'gke'
    }
    stages {
        stage("Checkout code") {
            steps {
                git branch: 'master',
                    url: "https://github.com/${env.USERGITHUB}/ci-workshop.git"
            }
        }
        stage("Unit test") {
            steps {
                withDockerContainer("python:3.6") {
                    sh """
                        cd src
                        pip install -r requirements.txt
                        python -m unittest
                    """
                }
            }
        }        
        stage("Build image") {
            steps {
                sh """
                    sed -i 's/USERID/${USERID}/g' Dockerfile
                """
                script {
                    myapp = docker.build("cicdday/${USERID}-hello:${env.BUILD_ID}")
                }
            }
        }
        stage("Push image") {
            steps {
                script {
                    docker.withRegistry('', 'dockerhub') {
                        myapp.push("${env.BUILD_ID}")
                    }
                }
            }
        }        
        stage('Deploy to GKE') {
            steps{
                sh """
                    sed -i 's/#USER#/${USERID}/g' CD-Workshop/rolling-update/deployment.yaml
                    sed -i 's/#APPUSER#/${USERID}-hello:${env.BUILD_ID}/g' CD-Workshop/rolling-update/deployment.yaml
                    sed -i 's/#DOCKER-HUB-USERNAME#/cicdday/g' CD-Workshop/rolling-update/deployment.yaml
                """
                
                step([$class: 'KubernetesEngineBuilder', 
                      projectId: env.PROJECTID, 
                      namespace: env.USERID,
                      clusterName: env.CLUSTERNAME, 
                      location: env.CLUSTERLOCATION, 
                      manifestPattern: 'CD-Workshop/rolling-update/deployment.yaml', 
                      credentialsId: env.CREDENTIALS_ID, 
                      verifyDeployments: true])
            }
        }
    }    
}