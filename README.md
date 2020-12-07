# Elastic Spring JPA CRUD Service - APM Demo

This demo is using a spring boot application which can be connected to H2 or MySQL as required. Follow the steps below to get this deployed to K8s and setup to be traced by Elastic APM. 

This demo also includes Elastic Real User Monitoring (RUM) JS agent. Unlike Elastic APM backend agents which monitor requests and responses, the RUM JavaScript agent monitors the real user experience and interaction within your client-side application. The RUM JavaScript agent is also framework-agnostic, which means it can be used with any frontend JavaScript application.

**Note: This demo is using a simple micro service deployed to K8s and we use Elastic Cloud (ESS) for our APM Server and the Elasticsearch cluster. In this example we use GKE as the K8s provider**

## Prerequisites

* pack CLI - https://buildpacks.io/docs/tools/pack/
* Elastic Cloud deployment with APM - https://cloud.elastic.co
* A K8s cluster 
* kubectl CLI - https://kubernetes.io/docs/tasks/tools/install-kubectl/
* mysql CLI (If using MySQL) - https://dev.mysql.com/downloads/shell/

## Table of Contents

* [Run With MySQL](#run-with-mysql)
* [Run With H2](#run-with-h2)

## Pre Steps for H2 or MySQL 

- Clone project and change into directory as follows

```bash 
$ git https://github.com/papicella/spring-crud-thymeleaf-demo.git
$ cd spring-crud-thymeleaf-demo
```

- Login to docker hub as follows

```bash
$ docker login -u DOCKER-HUB-USER -p PASSWD
```

- Package using Cloud Native Buildpacks. You could also use "kpack" to build images from withing K8s see this link for how [https://github.com/pivotal/kpack]. In the example below we just use pack CLI rather then kpack. Ensure you use your DOCKER-HUB-USER and replace that in the script below  

_Note: This will take some time for the first build._

```bash
$ ./mvnw -D skipTests package
$ pack build DOCKER-HUB-USER/elastic-springjpa-crud-mysqlservice:1.0 --builder paketobuildpacks/builder:base --publish --path ./target/elastic-springjpa-crud-mysql-0.0.1-SNAPSHOT.jar
```

## Run with MySQL

_Note: Make sure you have run the pre steps prior to running these steps_

- Using Helm install MySQL into the K8s cluster as follows

```bash 
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install pas-mysql bitnami/mysql
```

- Expose a LoadBalancer service for remote access to MySQL just to make access easier

```bash 
kubectl expose service pas-mysql --type LoadBalancer --port 3306 --target-port 3306 --name pas-mysql-public
```

-- Verify you can connect to the MySQL remotely by running the script "connect-to-mysql-k8s.sh". If it works it will look like this

```bash
$ ./connect-to-mysql.sh

mysql -h 1.1.0.3 -P3306 -u root -p******

mysql: [Warning] Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 163791
Server version: 8.0.22 Source distribution

Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql>
```

- Create a database / user as shown below to the connected MySQL instance

```sql
mysql> create database apples;
Query OK, 1 row affected (0.28 sec)

mysql> CREATE USER 'pas'@'%' IDENTIFIED BY 'pas';
Query OK, 0 rows affected (0.28 sec)

mysql> GRANT ALL PRIVILEGES ON apples.* TO 'pas'@'%' WITH GRANT OPTION;
Query OK, 0 rows affected (0.26 sec)
```

- Make sure your connected to your K8s cluster and run the following commands to create a K8s Secret and ConfigMap. Please replace values as shown in the list below

* APM-TOKEN
* APM-SERVER-URL
* APM-SERVER-PORT
* MYSQL-IP
* MYSQL-USER
* MYSQL-PASSWD

```bash
kubectl create secret generic apm-token-secret --from-literal=secret_token=APM-TOKEN

kubectl create configmap apm-agent-details --from-literal=server_urls=https://APM-SERVER-URL:APM-SERVER-PORT

kubectl create secret generic elastic-springjpa-crud-mysql --from-literal=mysql_url=jdbc:mysql://MYSQL-IP:3306/apples \
--from-literal=mysql_user=MYSQL-USER \
--from-literal=mysql_password=MYSQL_PASSWD
```

- Now we can deploy our service to K8s using the K8s YAML for deployment as follows. Replace DOCKER_HUB-USER with your user you used with pack CLI

_Note: The file springjpa-crud-mysql-deployment.yml is provided in the ROOT directory of the cloned project if you wish to use my container image that is ok_

**YAML**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: elastic-springjpa-crud-mysql-service
spec:
  selector:
    matchLabels:
      app: elastic-springjpa-crud-mysql-service
  replicas: 1
  template:
    metadata:
      labels:
        app: elastic-springjpa-crud-mysql-service
    spec:
      containers:
        - name: elastic-springjpa-crud-mysql-service
          image: DOCKER-HUB-USER/elastic-springjpa-crud-mysqlservice:1.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
          - name: ELASTIC_APM_ENABLE_LOG_CORRELATION
            value: "true"
          - name: ELASTIC_APM_CAPTURE_JMX_METRICS
            value: >-
              object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime:metric_name=collection_time],
              object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap]
          - name: ELASTIC_APM_SERVER_URLS
            valueFrom:
              configMapKeyRef:
                name: apm-agent-details
                key: server_urls
          - name: spring.profiles.active
            value: "MYSQL"
          - name: MYSQL_URL
            valueFrom:
              secretKeyRef:
                name: elastic-springjpa-crud-mysql
                key: mysql_url
          - name: MYSQL_USER
            valueFrom:
              secretKeyRef:
                name: elastic-springjpa-crud-mysql
                key: mysql_user
          - name: MYSQL_PASSWD
            valueFrom:
              secretKeyRef:
                name: elastic-springjpa-crud-mysql
                key: mysql_password
          - name: ELASTIC_APM_SERVICE_NAME
            value: "elastic-springjpa-crud-mysql-service"
          - name: ELASTIC_APM_APPLICATION_PACKAGES
            value: "pas.spring.demos"
          - name: ELASTIC_APM_SECRET_TOKEN
            valueFrom:
              secretKeyRef:
                name: apm-token-secret
                key: secret_token

---
apiVersion: v1
kind: Service
metadata:
  name: elastic-springjpa-crud-mysql-service-lb
  labels:
    name: elastic-springjpa-crud-mysql-service-lb
spec:
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
  selector:
    app: elastic-springjpa-crud-mysql-service
  type: LoadBalancer

```

- Deploy as Follows 

```bash
$ kubectl apply -f springjpa-crud-mysql-deployment.yml
```

- Once deployed check if LB service IP address is available as follows and the POD is running

```bash
$ k get pods
NAME                                                    READY   STATUS    RESTARTS   AGE
elastic-springjpa-crud-mysql-service-66dd4799d5-hk7nq   1/1     Running   0          31h
pas-mysql-master-0                                      1/1     Running   0          9d
pas-mysql-slave-0                                       1/1     Running   0          9d

$ kubectl get svc
NAME                      TYPE           CLUSTER-IP       EXTERNAL-IP      PORT(S)          AGE
elastic-springjpa-crud-mysql-service-lb   LoadBalancer   10.131.245.234   35.244.71.190    80:30116/TCP      31h
```

- Determine the LB IP to hit the home page using a command as follows

```bash
$ kubectl get service elastic-springjpa-crud-mysql-service-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}'
35.244.71.19
```

- Navigate to the home page and generate some traffic as well as HTTP 500 and HTTP 404 errors 

http://{IP-FROM-LAST-STEP}

![alt tag](https://i.ibb.co/t4Yv0dp/elastic-springjpa-crud-mysql-service-1.png)

- Open up APM to verify the elastic book spring boot service has been discovered as shown below

![alt tag](https://i.ibb.co/HHGjP4P/elastic-springjpa-crud-mysql-service-2.png)

![alt tag](https://i.ibb.co/Z120GDK/elastic-springjpa-crud-mysql-service-3.png)

![alt tag](https://i.ibb.co/47HK18h/elastic-springjpa-crud-mysql-service-4.png)

![alt tag](https://i.ibb.co/233WT1H/elastic-springjpa-crud-mysql-service-5.png)

![alt tag](https://i.ibb.co/crTnRQc/elastic-springjpa-crud-mysql-service-6.png)

![alt tag](https://i.ibb.co/JmkTWPc/elastic-springjpa-crud-mysql-service-7.png)

![alt tag](https://i.ibb.co/gy3RCzf/elastic-springjpa-crud-mysql-service-8.png)

## Run with H2

If you don't want to use MySQL H2 is fine in which case you would deploy it as follows.

_Note: Make sure you have run the pre steps prior to running these steps_

- Make sure your connected to your K8s cluster and run the following commands to create a K8s Secret and ConfigMap. Please replace values as shown in the list below

* APM-TOKEN
* APM-SERVER-URL
* APM-SERVER-PORT

```bash
kubectl create secret generic apm-token-secret --from-literal=secret_token=APM-TOKEN

kubectl create configmap apm-agent-details --from-literal=server_urls=https://APM-SERVER-URL:APM-SERVER-PORT
```

- Now we can deploy our service to K8s using the K8s YAML for deployment as follows. Replace DOCKER_HUB-USER with your user you used with pack CLI

_Note: The file h2-springjpa-crud-deployment.yml is provided in the ROOT directory of the cloned project if you wish to use my container image that is ok_

**YAML**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: elastic-springjpa-crud-mysql-service
spec:
  selector:
    matchLabels:
      app: elastic-springjpa-crud-mysql-service
  replicas: 1
  template:
    metadata:
      labels:
        app: elastic-springjpa-crud-mysql-service
    spec:
      containers:
        - name: elastic-book-service
          image: DOCKER_HUB-USER/elastic-springjpa-crud-mysqlservice:1.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
          - name: ELASTIC_APM_ENABLE_LOG_CORRELATION
            value: "true"
          - name: ELASTIC_APM_CAPTURE_JMX_METRICS
            value: >-
              object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime:metric_name=collection_time],
              object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap]
          - name: ELASTIC_APM_SERVER_URLS
            valueFrom:
              configMapKeyRef:
                name: apm-agent-details
                key: server_urls
          - name: ELASTIC_APM_SERVICE_NAME
            value: "elastic-springjpa-crud-mysql-service"
          - name: ELASTIC_APM_APPLICATION_PACKAGES
            value: "pas.spring.demos"
          - name: ELASTIC_APM_SECRET_TOKEN
            valueFrom:
              secretKeyRef:
                name: apm-token-secret
                key: secret_token

---
apiVersion: v1
kind: Service
metadata:
  name: elastic-springjpa-crud-mysql-service-lb
  labels:
    name: elastic-springjpa-crud-mysql-service-lb
spec:
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
  selector:
    app: elastic-springjpa-crud-mysql-service
  type: LoadBalancer
```

- Deploy as Follows

```bash
$ kubectl apply -f h2-springjpa-crud-deployment.yml
```

- Once deployed check if LB service IP address is available as follows and the POD is running

```bash
$ k get pods
NAME                                                    READY   STATUS    RESTARTS   AGE
elastic-springjpa-crud-mysql-service-66dd4799d5-hk7nq   1/1     Running   0          31h
pas-mysql-master-0                                      1/1     Running   0          9d
pas-mysql-slave-0                                       1/1     Running   0          9d

$ kubectl get svc
NAME                      TYPE           CLUSTER-IP       EXTERNAL-IP      PORT(S)          AGE
elastic-springjpa-crud-mysql-service-lb   LoadBalancer   10.131.245.234   35.244.71.190    80:30116/TCP      31h
```

-- Determine the LB IP to hit the home page using a command as follows

```bash
$ kubectl get service elastic-springjpa-crud-mysql-service-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}'
35.244.71.19
```

-- Navigate to the home page and generate some traffic as well as HTTP 500 and HTTP 404 errors 

http://{IP-FROM-LAST-STEP}

![alt tag](https://i.ibb.co/t4Yv0dp/elastic-springjpa-crud-mysql-service-1.png)

<hr />
Pas Apicella [pas.apicella at elastic.co] is an Solution Architect at Elastic APJ  