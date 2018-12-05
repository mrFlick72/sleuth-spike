# sleuth-spike
Spike on Spring Cloud Sleuth

In this repo I explore the Spring Cloud Sleuth capabilities. The first feature explored is about log aggregation/correlation.
First of all may be useful understand what problem Spring Cloud Sleuth solve. Spring Cloud Sleuth solve the problem of **distributed tracing** in a Spring Cloud application.

In a monolithic application the log and service call tracing is a relative simple problem to solve, all service flow is implemented 
on the same system. However in a distributed system we may need, during trouble shutting, follow log of different application 
that cooperate in the system. A typical pattern involve a correlation id in the log messages and use this correlation id in order to 
understand in the log flow and correlate the log events in a common flow like in a monolithic application log events.

Since that Sleuth involve Spring Cloud application it is perfectly suitable to be used in a real Cloud Native application. 
A perfect 12-Factor application should involve log aggregation in particular we need of a system for read log without 
access to the server in order to read logs. A popular solution involve the ELK (Elastic Search Logstash Kibana) stack.
Reading the official [Spring documentation](https://cloud.spring.io/spring-cloud-static/Finchley.SR2/single/spring-cloud.html#_spring_cloud_sleuth)
 configure ELK is very simple we only need configure a logback-spring.xml and spring.application.name property 
 in the bootstrap.yml/properties and the game is done!. However It is so simple like no so simple. This configuration is enough for formatting the log 
 but is necessary then send those logs to Logstash. In this step there is the pitfall, the official spring documentation do not show how configure Logstash. 
 Typically we can configure in two way the log crawling the first more simple is configure Logstash input pipeline to watch the path in which the your application will write logs. 
 This solution is simple but not so good, Logstash is a very powerful product but having it configured in any application server do not scale well in this case we use it as a sort of agent in the application server, if we want send log, Filebeat 
 probably is a better solution. The primary problem of install Logstash in any server is that we should configure the Logstash pipeline and maintain it in any application server. 
 For sending log Filebeat is enough and more suitable, it belongs to a specific product family called Beats that is designed just for these use cases. 
 If we do not want install Filebeat in our application server we can congure the Logstash pipeline with an input channel in tcp on the port 5000 and then configure a custom appender in our application that send log events via TCP on Logstash.
 
 For the logback-spring.xml we can configure it like below:
 
 ```xml
 <?xml version="1.0" encoding="UTF-8"?>
 <configuration>
     <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
     ​
     <springProperty scope="context" name="springAppName" source="spring.application.name"/>
     <springProperty scope="context" name="logFile" source="logging.folder"/>
     <!-- Example for logging into the build folder of your project -->
     <property name="LOG_FILE" value="${logFile}/${springAppName}"/>​
 
     <!-- You can override this to have a custom pattern -->
     <property name="CONSOLE_LOG_PATTERN"
               value="%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(${LOG_LEVEL_PATTERN:-%5p}) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}"/>
 
     <!-- Appender to log to console -->
     <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
         <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
             <!-- Minimum logging level to be presented in the console logs-->
             <level>DEBUG</level>
         </filter>
         <encoder>
             <pattern>${CONSOLE_LOG_PATTERN}</pattern>
             <charset>utf8</charset>
         </encoder>
     </appender>
     <!-- Appender to log to file -->​
     <appender name="flatfile" class="ch.qos.logback.core.rolling.RollingFileAppender">
         <file>${LOG_FILE}</file>
         <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
             <fileNamePattern>${LOG_FILE}.%d{yyyy-MM-dd}.gz</fileNamePattern>
             <maxHistory>7</maxHistory>
         </rollingPolicy>
         <encoder>
             <pattern>${CONSOLE_LOG_PATTERN}</pattern>
             <charset>utf8</charset>
         </encoder>
     </appender>
     ​
     <!-- Appender to log to file in a JSON format -->
     <appender name="logstash" class="ch.qos.logback.core.rolling.RollingFileAppender">
         <file>${LOG_FILE}.json</file>
         <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
             <fileNamePattern>${LOG_FILE}.json.%d{yyyy-MM-dd}.gz</fileNamePattern>
             <maxHistory>7</maxHistory>
         </rollingPolicy>
         <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
             <providers>
                 <timestamp>
                     <timeZone>UTC</timeZone>
                 </timestamp>
                 <pattern>
                     <pattern>
                         {
                         "severity": "%level",
                         "service": "${springAppName:-}",
                         "trace": "%X{X-B3-TraceId:-}",
                         "span": "%X{X-B3-SpanId:-}",
                         "parent": "%X{X-B3-ParentSpanId:-}",
                         "exportable": "%X{X-Span-Export:-}",
                         "pid": "${PID:-}",
                         "thread": "%thread",
                         "class": "%logger{40}",
                         "rest": "%message"
                         }
                     </pattern>
                 </pattern>
             </providers>
         </encoder>
     </appender>
     
     
     <appender name="stash" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
         <destination>localhost:5000</destination>
         <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
     </appender>
 
     ​
     <root level="INFO">
         <appender-ref ref="stash"/>
         <appender-ref ref="console"/>
          <!--uncomment this to have also JSON logs -->
         <appender-ref ref="flatfile"/>
     </root>
 </configuration>
 ```
 
 the Logstash pipeline can be something like below

 ```yaml
 input {
  tcp {
    port => 5000
    }
 }
 
 ## Add your filters / logstash plugins configuration here
 filter {
    # pattern matching logback pattern
    grok {
      match => { "message" => "%{TIMESTAMP_ISO8601:timestamp}\s+%{LOGLEVEL:severity}\s+\[%{DATA:service},%{DATA:trace},%{DATA:span},%{DATA:exportable}\]\s+%{DATA:pid}\s+---\s+\[%{DATA:thread}\]\s+%{DATA:class}\s+:\s+%{GREEDYDATA:rest}" }
      }
 }
 
 output {
    elasticsearch {
    hosts => "elasticsearch:9200"
  }
 }
 ```