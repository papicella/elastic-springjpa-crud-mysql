# Elastic Spring JPA CRUD Service - APM Demo

This demo is using a spring boot application which can be connected to H2 or MySQL as required. Follow the steps below to get this deployed to K8s and setup to be traced by Elastic APM

**Note: This demo is using a simple micro service deployed to K8s and we use Elastic Cloud (ESS) for our APM Server and Elasticsearch cluster. In this example we use GKE as the K8s provider**

## Prerequisites

* pack CLI - https://buildpacks.io/docs/tools/pack/
* Elastic Cloud deployment with APM - https://cloud.elastic.co

## Steps

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
$ pack build pasapples/elastic-springjpa-crud-mysqlservice:1.0 --builder paketobuildpacks/builder:base --publish --path ./target/elastic-springjpa-crud-mysql-0.0.1-SNAPSHOT.jar
```

### Steps for MySQL

- Using Helm install MySQL into the K8s cluster as follows

```bash 
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install pas-mysql bitnami/mysql
```

- Expose a LoadBalancer service for remote access to MySQL just to make access easier

```bash 
kubectl expose service pas-mysql --type LoadBalancer --port 3306 --target-port 3306 --name pas-mysql-public
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


<hr />
Pas Apicella [pas.apicella at elastic.co] is an Solution Architect at Elastic APJ  