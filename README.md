# SmartchainDB Driver

## Initial Project Setup
1. Setup a Java 8 environment
```bash
sudo apt update
sudo apt install -y zip unzip wget

sudo apt install openjdk-8-jdk
sudo update-alternatives --config java 
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
```
1. Download and install Gradle
```bash
wget https://services.gradle.org/distributions/gradle-3.0-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
ls /opt/gradle/gradle-3.0
```
1. Set up the environment variables for Gradle. Create a file at ```/etc/profile.d/gradle.sh``` and add the following lines to it:
```bash
export GRADLE_HOME=/opt/gradle/gradle-3.0 
export PATH=${GRADLE_HOME}/bin:${PATH}
```
1. Make the script executable and source it:
```bash
sudo chmod +x /etc/profile.d/gradle.sh
source /etc/profile.d/gradle.sh
```
1. Check Gradle version
```bash
gradle -v
```

## Run Project
To run the project, run command below:

```bash
gradle run
```

The command above will run ```com.bigchaindb.smartchaindb.driver.ProcessesRunner``` class which will spawn 1 new process which runs the code in ```com.bigchaindb.smartchaindb.driver.BigchainDBJavaDriver``` class. 

If you want to spawn multiple processes then run:
```bash
gradle run -PnumProcesses=<int>
```
Replace ```<int>``` with the number of processes to run. Usually the number of CPUs or vCPUs on your machine or even more.
