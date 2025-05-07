# run chmod +x install_dependencies_driver_new.sh
# then run  ./install_dependencies_driver_new.sh

sudo apt update
sudo apt install -y zip
sudo apt install -y unzip

#Install JAVA 8
sudo apt install openjdk-8-jdk

sudo update-alternatives --config java 
#chose java-8

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

wget https://services.gradle.org/distributions/gradle-3.0-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
ls /opt/gradle/gradle-3.0

#copy the 2 lines below onto your clipboard
export GRADLE_HOME=/opt/gradle/gradle-3.0 
export PATH=${GRADLE_HOME}/bin:${PATH}

sudo nano /etc/profile.d/gradle.sh # and paste the 2 lines above into the file then save and exit

sudo chmod +x /etc/profile.d/gradle.sh
source /etc/profile.d/gradle.sh
gradle -v