package com.kafka.algo;

import com.kafka.algo.runner.configreader.KafkaConfigReader;
import com.kafka.algo.runners.KafkaAlgoAppRunner;

public class KafkaAlgoApp2 {

	public static void main(String[] args) {

		KafkaConfigReader configReader = new KafkaConfigReader();
		String inputTopicName = configReader.getTopics().get("input-topic-2");
		String outputTopicName = configReader.getTopics().get("output-topic-2");
		KafkaAlgoAppRunner runner = new KafkaAlgoAppRunner(inputTopicName, outputTopicName, configReader);
		runner.start();

	}

}