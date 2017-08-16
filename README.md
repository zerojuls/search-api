# Search API [![CircleCI](https://circleci.com/gh/VivaReal/search-api/tree/master.svg?&style=shield&circle-token=ba04762cae23d66aa73b715ef66562f0928dfafb)](https://circleci.com/gh/VivaReal/search-api/tree/master)

![Searching](https://github.com/VivaReal/search-api/raw/master/src/main/resources/static/house-search.jpg "House Searching")

Search API version 2.

Search API is a API created with Spring boot framework that does queries to our search engine. To generating a client, we should use [Haxe](https://haxe.org) cross-platform toolkit.

## Configuring project

### Eclipse

- Run the command `./gradlew cleanEclipse eclipse`;
- import project at Eclipse;

### IDEA

- import project as gradle project using `build.gradle` file and be happy <3

# How to Run

You can run with many ways:

> You must pass the `es.cluster.name` parameter. (Assuming that the cluster name is `tincas`)

## Gradle

```sh
./gradlew bootRun -Des.cluster.name=tincas
```

## Java Run

```sh
java -Des.cluster.name=tincas -jar build/libs/search-api.jar
```

## Docker

```sh
docker run --rm -it -p 8482:8482 \
   -e JAVA_OPTS='-Des.cluster.name=tincas' \
   vivareal/search-api-v2:<VERSION>
```

# How to Test

```sh
./gradlew test
```

# How to Deploy

## Deploying from Jenkins
 
<a href="http://jenkins.vivareal.com/view/SEARCH-API/job/SEARCH_API_V2_PROD/build?delay=0sec">
  <img src="http://ftp-chi.osuosl.org/pub/jenkins/art/jenkins-logo/logo+title.svg" alt="Jenkins" width="150">
</a> 

## Deploying from your terminal

**Make sure you've pushed the docker image to dockerhub before deploying the environment.**

```
make ENV=${ENV} IMAGE_NAME=${IMAGE_NAME} STACK_ALIAS=${STACK_ALIAS} AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION} ES_CLUSTER_NAME=${ES_CLUSTER_NAME} deploy
```

Possible values for ENV: `prod` or `qa`.

`IMAGE_NAME` is a string with the image pushed to Dockerhub

`STACK_ALIAS` is a string used to name Cloudformation stack. If not present, the hash of the current commit will be used to identify the stack.

`AWS_DEFAULT_REGION` is a string with the AWS region to deploy. (Eg. `sa-east-1`)

`ES_CLUSTER_NAME` is a string with the current [ElasticSearch](https://github.com/VivaReal/search-es) cluster.

## The Query language syntax

SearchAPI provides a Query DSL creating flexible queries to our search engine. The idea is to abstract any kind of complexity to making queries and search optimizations. 

Basically, you need just use `filter` query parameter for that, for example: 

```sh
curl -X GET http://api/v2/listings?filter=field1 EQ 'value1' AND (field2 EQ 'value2'OR field3 EQ 'value3')
```

SearchAPI parses this query using different kind of parsers and generates an Abstract Syntax Tree with the query fragments. To explanation the query fragments, please see the image below:


![QueryDSL](https://github.com/VivaReal/search-api/raw/master/src/main/resources/static/query-dsl.png "Query DSL")

You can see more details in [wiki](https://github.com/VivaReal/search-api/wiki).
