// call is the default function name
 def call (Map configMap){
    pipeline {
    // These are pre-build section
    agent {
        node {
            label 'AGENT-1'
        }
    } 
    environment {
        COURSE = "Jenkins"
        appVersion = ""
        ACC_ID = "445567085619"
        PROJECT = configMap.get("project")
        COMPONENT = configMap.get("component")
    }
    options {
        timeout(time:10, unit:'MINUTES')
        disableConcurrentBuilds()

    }
    // parameters {
    // string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
    // text(name: 'BIOGRAPHY', defaultValue: '', description: 'Enter some information about the person')
    // booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Toggle this value')
    // choice(name: 'CHOICE', choices: ['One', 'Two', 'Three'], description: 'Pick something')
    // password(name: 'PASSWORD', defaultValue: 'SECRET', description: 'Enter a password')
    // }


    // This is build section

    stages{
        stage('Read Version') {
        steps{
            script {
                // appVersion = readFile(file: 'version')
                appVersion = readFile(file:'version')

                echo "app version: ${appVersion}"
            }
        }
    }  

    stage('Install dependencies'){
        steps{
            script {
                sh """
                  pip3 install -r requirements.txt

                  """

            }
        }
    }

    stage('Unit test'){
        steps{
            script{
                sh """
                   
                   echo test

                   """
            }
        }
    }

        //Here you need to select scanner tool and send the analysis to server
        //  stage('Sonar Scan'){
        //     environment {
        //         def scannerHome = tool 'sonar-8.0'
        //     }
        //     steps {
        //         script{
        //             withSonarQubeEnv('sonar-server') {
        //                 sh  "${scannerHome}/bin/sonar-scanner"
        //             }
        //         }
        //     }
        // }

        // stage('Quality Gate') {
        //     steps {
        //         timeout(time: 1, unit: 'HOURS') {
        //             // Wait for the quality gate status
        //             // abortPipeline: true will fail the Jenkins job if the quality gate is 'FAILED'
        //             waitForQualityGate abortPipeline: true 
        //         }
        //     }
        // } 

        stage('Dependabot Security Gate') {
            when {
                expression { false }
            }
            environment {
                GITHUB_OWNER = 'siva3150'
                GITHUB_REPO  = 'catalogue-86s'
                GITHUB_API   = 'https://api.github.com'
                GITHUB_TOKEN = credentials('GITHUB_TOKEN')
            }

            steps {
                script{
                    /* Use sh """ when you want to use Groovy variables inside the shell.
                    Use sh ''' when you want the script to be treated as pure shell. */
                    sh '''
                    echo "Fetching Dependabot alerts..."

                    response=$(curl -s \
                        -H "Authorization: token ${GITHUB_TOKEN}" \
                        -H "Accept: application/vnd.github+json" \
                        "${GITHUB_API}/repos/${GITHUB_OWNER}/${GITHUB_REPO}/dependabot/alerts?per_page=100")

                    echo "${response}" > dependabot_alerts.json

                    high_critical_open_count=$(echo "${response}" | jq '[.[] 
                        | select(
                            .state == "open"
                            and (.security_advisory.severity == "high"
                                or .security_advisory.severity == "critical")
                        )
                    ] | length')

                    echo "Open HIGH/CRITICAL Dependabot alerts: ${high_critical_open_count}"

                    if [ "${high_critical_open_count}" -gt 0 ]; then
                        echo "❌ Blocking pipeline due to OPEN HIGH/CRITICAL Dependabot alerts"
                        echo "Affected dependencies:"
                        echo "$response" | jq '.[] 
                        | select(.state=="open" 
                        and (.security_advisory.severity=="high" 
                        or .security_advisory.severity=="critical"))
                        | {dependency: .dependency.package.name, severity: .security_advisory.severity, advisory: .security_advisory.summary}'
                        exit 1
                    else
                        echo "✅ No OPEN HIGH/CRITICAL Dependabot alerts found"
                    fi
                    '''
                    
                }
            }
        }

        stage('Build Image') {
            steps {
                script{
                    withAWS(region:'us-east-1',credentials:'aws-creds') {
                        sh """
                            aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                            docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                            docker images
                            docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                        """
                    }
                }
            }
        }

        //  stage('Trivy Scan'){
        //     steps {
        //         script{
        //             sh """
        //                 trivy image \
        //                 --scanners vuln \
        //                 --severity HIGH,CRITICAL,MEDIUM \
        //                 --pkg-types os \
        //                 --exit-code 1 \
        //                 --format table \
        //                 ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
        //             """
        //         }
        //     }
        // }

      

        // stage('Build'){
        //     steps {
        //         script {
        //             """
        //             echo "Building"
        //             echo $COURSE
        //             sleep 10
        //             env

        //         // echo "Hello ${params.PERSON}"
        //         // echo "Biography: ${params.BIOGRAPHY}"
        //         // echo "Toggle: ${params.DEPLOY}"
        //         // echo "Choice: ${params.CHOICE}"
        //         // echo "Password: ${params.PASSWORD}"

        //             """
        //         }
        //     }
        // }

         stage ('Trigger Deploy'){
            steps {
                script{
                    build job: "../${COMPONENT}-deploy",
                    wait: false, //wait for completion
                    propagate: false, //propagation status
                    parameters: [
                        string(name: 'appVersion', value: "${appVersion}"),
                        string(name: 'deploy_to', value: "dev")
                    ]
                }
            }
         }
    }
          // This is post-build section
        post{
            always{
                echo 'I will run always say Hello Again!'
                cleanWs()
            }
            success{
                echo 'I will run if pipeline success'
            }
            failure{
                echo 'I will run if pipeline failure'
            }
            aborted{
                echo 'Pipeline is aborted'
            }
    }
  }
}