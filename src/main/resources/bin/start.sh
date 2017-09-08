#! /bin/sh
cd `dirname $0`
PWD=`pwd`

for i in lib/*.jar;
do CLASSPATH="$CLASSPATH":$PWD/$i;
done

for opt in "$@"
do JAVA_OPTS="$JAVA_OPTS -D$opt";
done

nohup java -server $JAVA_OPTS -classpath .:static:$CLASSPATH com.github.sosozhuang.ChatMain >/dev/null 2>&1 &