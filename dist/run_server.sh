if [[ -z "$1" ]] ; then
  java -jar server.jar Runner -ORBInitialPort 1050 ip="$1"
else
  java -jar server.jar Runner -ORBInitialPort 1050
fi


