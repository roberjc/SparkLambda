# TFM Máster Arquitectura Big Data - Roberto Jiménez

El proyecto trata de dar solución a la necesidad de conocer en tiempo real la temperatura de todos los sectores que componen una parcela de cultivo.
Para ello se lleva a cabo la recolección de datos provenientes de sensores, su procesado para obtener una medición media por sector y la monitorización en un mapa de calor.

# Diagrama de la arquitectura
En un principio se definió una arquitectura Lambda, la cual se basó en un flujo streaming con el cuál se construiría un histórico de datos y se generaría un modelo para aplicar a dicho flujo. En otras palabras, se utilizaría un procesado batch para categorizar un procesado streaming.

Finalmente, el dominio del problema se acotó y la arquitectura pasó a ser Kappa. El flujo streaming se sigue categorizando tras su procesado pero en base a unas reglas fijas, no provenientes de ningún modelo. Aun así, sigue presente la construcción de un histórico de datos para futuros casos de uso.

Se ha construído un cluster virtualizando 3 máquinas Centos y siguiendo la jerarquía de 1 maestro y 2 esclavos. En adelante: master, slave1 y slave2, respectivamente.

El nodo master alberga los servicios:
 - QuorumPeerMain
 - Kafka
 - ConnectStandalone
 - ResourceManager
 - NameNode
 - SecondaryNameNode
 - ConnectStandalone
 - Mosquitto
 
Los nodos slave1 y slave2:
 - NodeManager
 - QuorumPeerMain
 - DataNode
 - Kafka

# Diagrama de estados
Los datos son generados sintéticamente teniendo en cuenta el momento del día y siguiendo una progresión razonable de la temperatura para cada sensor. Los datos generados son enviados a un broker Mosquitto mediante protocolo MQTT. Mosquitto no garantiza la alta disponibilidad, aunque contempla el modo puente para, por ejemplo, tener levantado en un nodo edge un broker que redireccione los mensajes a otro broker del cluster. En cualquier caso sólo se ha levantado un broker.
El siguiente paso es mover los datos que llegan al broker Mosquitto a un sistema similar y que permita ser explotado más adelante por el motor de procesamiento. 

Se trata de Kafka, el cual se rige tambień por topics pero sigue el funcionamiento de publicador/suscriptor
Kafka incorpora la librería Kafka Connect, la cual permite, mediante conectores a distintos tipos de fuentes, "mapear" los datos a sus topics. En este caso se utiliza un conector de terceros para portar los datos del topic MQTT al topic de Kafka.
Kafka connect es soportado en modo de alta disponibilidad, aunque con el conector MQTT no fue posible hacerlo funcionar más que en modo Standalone.

Ya se entra de lleno en el motor de procesamiento, que en este caso se trata de Spark Streaming. Se ha optado por usar el API Structured Streaming por cómo simplifica el flujo de la aplicación.
Spark comienza una ETL por la lectura del topic de Kafka que porta los datos y a continuación recupera el esquema de datos del sensor contenido en el esquema de datos del topic de Kafka. Se realizan una serie de transformaciones para que los datos sean legibles y se modelan en la aplicación.

El flujo sigue con las siguientes etapas de carga: Raw y Procesado.
Para la primera etapa, Raw, los datos se persisten una vez modelados en el sistema HDFS, de esta manera siempre se podrá llegar a cualquier estado de procesamiento posterior.

La segunda etapa de carga realiza un procesamiento sobre los datos para obtener valor, de modo que establece ventanas de 30 segundos en las que agrupa los datos por su fecha de generación y el sector al que pertenecen. Sobre estas ventanas calcula una media de las temperaturas por sector y cuenta el número de registros con los que ha obtenido el cálculo. Se establece una marca de agua de hasta 40 segundos, para que los datos que lleguen con retraso se contemplen en su correspondiente ventana.
Una vez procesados los datos, éstos llegan al final de la segunda etapa de carga, la cual se trata de un microservicio que persiste los datos de forma temporal en un fichero con formato JSON.
Dicho API es consultado por la vista final en forma de mapa, el cual se carga con la información recogida y se actualiza conforme los datos son reprocesados.

### Despliegue

El proyecto requiere SBT y Docker para ejecutarse y SSH para copiarlo al cluster, entre otras cosas.

Para levantar los servicios del cluster:
```sh
$ ssh root@master
$ sh start-cluster.sh
```

Para el Job de Spark:
```sh
$ cd real-time-spark
$ sbt package
$ scp target/scala/scala-2.11/tfm_2.11-0.1.jar root@master:<directorio_ejecucion>
```

Para el microservicio al que hace peticiones el mapa:
```sh
$ docker pull williamyeh/json-server
$ touch <directorio_usuario>/db.json
$ docker run -p 3000:3000 -v <directorio_usuario>/:/data williamyeh/json-server --watch db.json
```

### Ejecución

El proyecto requiere SBT y Docker para ejecutarse y SSH para copiarlo al cluster.

Para el Job de Spark:
```sh
$ ssh root@master
$ sh run.sh
```
Para producir mensajes de sensores:
```sh
$ cd real-time-sensor
$ sbt run
```

Para el microservicio al que hace peticiones el mapa:
```sh
$ docker ps -a
$ docker start <id-contenedor>
```

### Componentes

La aplicación hace uso de una serie de componentes para distintas funciones:

* [clush] - Lanzar comandos a lo largo del cluster
* [typesafe-config] - Establecer variables de configuración del entorno
* [scalaj] - Realizar peticiones HTTP
* [paho] - Implementar sensores MQTT
* [play] - Gestionar formato JSON
* [spark] - Procesar el flujo streaming
* [kafka] - Distribuir mensajes

Verifica que funcione la ejecución navegando desde el navegador a la dirección:

```sh
https://htmlpreview.github.io/?https://github.com/roberjc/real-time-map/blob/master/real-time-map
```


**TFM Máster Arquitectura Big Data - Roberto Jiménez**

   [PlDb]: <https://github.com/joemccann/dillinger/tree/master/plugins/dropbox/README.md>
   [PlGh]: <https://github.com/joemccann/dillinger/tree/master/plugins/github/README.md>
   [PlGd]: <https://github.com/joemccann/dillinger/tree/master/plugins/googledrive/README.md>
   [PlOd]: <https://github.com/joemccann/dillinger/tree/master/plugins/onedrive/README.md>
   [PlMe]: <https://github.com/joemccann/dillinger/tree/master/plugins/medium/README.md>
   [PlGa]: <https://github.com/RahulHP/dillinger/blob/master/plugins/googleanalytics/README.md>
   [clush]: <http://clustershell.readthedocs.io/en/latest/tools/clush.html>
   [typesafe-config]: <https://github.com/lightbend/config/blob/master/README.md>
   [scalaj]: <https://github.com/scalaj/scalaj-http/blob/master/README.md>
   [paho]: <https://github.com/eclipse/paho.mqtt.java/blob/master/README.md>
   [play]: <https://github.com/playframework/play-json/blob/master/README.md>
   [spark]: <https://spark.apache.org/>
   [kafka]: <https://kafka.apache.org/>

