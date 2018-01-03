# Prometheus Listener for Jmeter

## Features
A listener for Jmeter to expose the results of your test through a Prometheus Api.

* Exposes Sampler latency and Assertion counts as metric values
* Can use sample_variables for additional labels.

```
# HELP jmeter_assertions_total Counter for assertions
# TYPE jmeter_assertions_total summary
jmeter_assertions_total_count{sampler_name="local-metrics",failure="false",assertion_name="Response Assertion",} 9.0
jmeter_assertions_total_sum{sampler_name="local-metrics",failure="false",assertion_name="Response Assertion",} 132.0
# HELP jmeter_samples_latency Summary for Sample Latency
# TYPE jmeter_samples_latency summary
jmeter_samples_latency{sampler_name="local-metrics",code="200",success="true",quantile="0.5",} 5.0
jmeter_samples_latency{sampler_name="local-metrics",code="200",success="true",quantile="0.99",} 11.0
jmeter_samples_latency_count{sampler_name="local-metrics",code="200",success="true",} 9.0
jmeter_samples_latency_sum{sampler_name="local-metrics",code="200",success="true",} 132.0
```

## Examples in Grafana
A grafana dashboard example to inspect jmeter exporter metrics. You can find more examples [here](https://github.com/johrstrom/jmeter-prometheus-plugin/tree/master/dashboard)
![JMeter dashboard](/dashboard/JMeter_screen.png?raw=true "JMeter dashboard")

## Usage

To use this plugin you'll have to clone this repo, build the shaded jar and move it to your JMeter's lib directory.

To build, simply maven package:
```
mvn clean package
```
This creates 2 jars, a shaded jar that has all the dependencies within it (this is the one you want) and the original jar. Both are in the target directory.  Simply move the jar to your $JMETER_HOME/lib directory as with any JMeter plugin and you're ready to go!



## Configuration

Specify these configuration properties in user.properties (or jmeter.properties, but that's considered bad practice) to override the port you bind, the threads the http server uses and more.

|Property Name |Description|Default Value|
|:----------------:|:-------------------------------------------:|:------------------:|
prometheus.clean.freq|Long running tests have memory issues, so to aleviate that, you can clear things that haven't been updated in a long time. Metrics that haven't been updated after this value in (ms) will be removed or cleared from the Prometheus registry, thus freeing memory. Setting it to 0 means you never clear anything.| 0|
|prometheus.port      | The port the Jetty server will bind to | 9207 |
|prometheus.thread.min| The Max number of threads for the Jetty server. This will cause errors if it's less than 4. | 4|
|prometheus.thread.max| The Minimum number of threads for the Jetty server| 4|


## Feedback

Feel free to open issues against this project.  It's not super active, mind you, but I'm trying to rectify that.
