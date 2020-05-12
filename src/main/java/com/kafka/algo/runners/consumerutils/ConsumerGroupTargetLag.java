package com.kafka.algo.runners.consumerutils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;

import com.kafka.algo.runners.configreader.KafkaConfigReader;
import com.kafka.algo.runners.kafkautils.KafkaConnection;

import static com.kafka.algo.runners.constants.Constants.NO_GROUP_FOUND;

/**
 * @author justin
 *
 * @param <K>
 * @param <V>
 */
public class ConsumerGroupTargetLag<K, V> {
	private AdminClient client;
	private KafkaConfigReader configReader;
	private KafkaConsumer<K, V> consumer;
	private String inputTopicName;

	/**
	 * @param inputTopicName
	 * @param consumer
	 * @param configReader
	 */
	public ConsumerGroupTargetLag(String inputTopicName, final KafkaConfigReader configReader) {
		this.configReader = configReader;
		this.inputTopicName = inputTopicName;
		this.client = AdminClient.create(kafkaProperties());
		this.consumer = new KafkaConsumer<>(KafkaConnection.getKafkaTargetConsumerProperties(configReader));
	}

	/**
	 * @return
	 */
	private Properties kafkaProperties() {
		Properties props = new Properties();
		props.put("bootstrap.servers", this.configReader.getBootstrapTargetServers());
		return props;
	}

	/**
	 * @param groupId
	 * @return
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private Map<TopicPartition, OffsetAndMetadata> getOffsetMetadata(String groupId)
			throws InterruptedException, ExecutionException {
		return this.client.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
	}

	/**
	 * @param partitionInfo
	 * @return
	 */
	private Map<TopicPartition, Long> getPartitionEndOffsets(Collection<TopicPartition> partitionInfo) {
		return this.consumer.endOffsets(partitionInfo);
	}

	/**
	 * @return Returns the first consumer group that subscribed to the topic which
	 *         is stable. Performance Implications.
	 * @return consumerGroupList processing the topic
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */

	private String getConsumerGroups() {

		String consumerGroup = null;
		try {
			Iterator<ConsumerGroupListing> initialItr = this.client.listConsumerGroups().all().get().iterator();
			while (initialItr.hasNext()) {
				ConsumerGroupListing consumerGroupListing = initialItr.next();
				Iterator<Entry<TopicPartition, OffsetAndMetadata>> itr = this.client
						.listConsumerGroupOffsets(consumerGroupListing.groupId()).partitionsToOffsetAndMetadata().get()
						.entrySet().iterator();
				while (itr.hasNext()) {
					Entry<TopicPartition, OffsetAndMetadata> itrNext = itr.next();
					if (itrNext.getKey().topic().equals(this.inputTopicName)) {
						Iterator<Entry<String, KafkaFuture<ConsumerGroupDescription>>> itrInside = this.client
								.describeConsumerGroups(Arrays.asList(consumerGroupListing.groupId())).describedGroups()
								.entrySet().iterator();
						while (itrInside.hasNext()) {
							Entry<String, KafkaFuture<ConsumerGroupDescription>> describeConsumer = itrInside.next();
							if (describeConsumer.getValue().get().state().toString().equals("Stable")) {
								return consumerGroupListing.groupId();
							}
						}
					}
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		return consumerGroup == null ? NO_GROUP_FOUND : consumerGroup;
	}

	/**
	 * @return This will return all consumer groups as a List<String> that
	 *         subscribed to the topic. Possible Performance Issue because of entire
	 *         iteration of consumer groups.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private List<String> getConsumerGroupList() {

		List<String> consumerGroupList = new ArrayList<String>();
		try {
			Iterator<ConsumerGroupListing> initialItr = this.client.listConsumerGroups().all().get().iterator();
			while (initialItr.hasNext()) {
				ConsumerGroupListing consumerGroupListing = initialItr.next();
				Iterator<Entry<TopicPartition, OffsetAndMetadata>> itr = this.client
						.listConsumerGroupOffsets(consumerGroupListing.groupId()).partitionsToOffsetAndMetadata().get()
						.entrySet().iterator();
				while (itr.hasNext()) {
					Entry<TopicPartition, OffsetAndMetadata> itrNext = itr.next();
					if (itrNext.getKey().topic().equals(this.inputTopicName)) {
						Iterator<Entry<String, KafkaFuture<ConsumerGroupDescription>>> itrInside = this.client
								.describeConsumerGroups(Arrays.asList(consumerGroupListing.groupId())).describedGroups()
								.entrySet().iterator();
						while (itrInside.hasNext()) {
							Entry<String, KafkaFuture<ConsumerGroupDescription>> describeConsumer = itrInside.next();
							if (describeConsumer.getValue().get().state().toString().equals("Stable")) {
								consumerGroupList.add(consumerGroupListing.groupId());
							}
						}

					}
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		return consumerGroupList;
	}

	/**
	 * {This method will return the lag of active consumer that prolvided in this}
	 * 
	 * @param groupId
	 * @return
	 */
	private long getConsumerGroupLag(String groupId) {

		long totalLag = 0L;
		try {
			Map<TopicPartition, OffsetAndMetadata> consumerGroupOffsets = getOffsetMetadata(groupId);
			Map<TopicPartition, Long> topicEndOffsets = getPartitionEndOffsets(consumerGroupOffsets.keySet());
			Iterator<Entry<TopicPartition, OffsetAndMetadata>> consumerItr = consumerGroupOffsets.entrySet().iterator();
			while (consumerItr.hasNext()) {
				Entry<TopicPartition, OffsetAndMetadata> partitionData = consumerItr.next();

				long lag = topicEndOffsets.get(partitionData.getKey()) - partitionData.getValue().offset();
				if (lag < 0) {
					lag = 0;
				}
				totalLag = totalLag + lag;
			}

		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return totalLag;
	}

	/**
	 * {This method will return the lag of active consumer that provided in this as
	 * a list}
	 * 
	 * @param groupId
	 * @return
	 */
	private long getConsumerGroupsLag(List<String> groupIdList) {
		TreeSet<Long> lagSet = new TreeSet<Long>();
		try {
			Iterator<String> groupItr = groupIdList.iterator();
			while (groupItr.hasNext()) {
				String groupId = groupItr.next();
				long totalLag = 0L;

				Map<TopicPartition, OffsetAndMetadata> consumerGroupOffsets = getOffsetMetadata(groupId);
				Map<TopicPartition, Long> topicEndOffsets = getPartitionEndOffsets(consumerGroupOffsets.keySet());
				Iterator<Entry<TopicPartition, OffsetAndMetadata>> consumerItr = consumerGroupOffsets.entrySet()
						.iterator();
				while (consumerItr.hasNext()) {
					Entry<TopicPartition, OffsetAndMetadata> partitionData = consumerItr.next();
					long lag = topicEndOffsets.get(partitionData.getKey()) - partitionData.getValue().offset();
					if (lag < 0) {
						lag = 0;
					}
					totalLag = totalLag + lag;
				}
				lagSet.add(totalLag);

			}

		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return lagSet.first();
	}

	/**
	 * This will return lag of any one of the consumer group that is active for this
	 * topic. This will be 0 if there is no groups available.
	 * 
	 * @return
	 */
	public long getAnyActiveConsumerLag() {
		String groupId = getConsumerGroups();
		return groupId.equals(NO_GROUP_FOUND) ? 0L : getConsumerGroupLag(groupId);
	}

	/**
	 * This will return lag of of the consumer group that is active and less in any
	 * of all for this topic. This will be 0 if there is no groups available.
	 * 
	 * @return
	 */
	public long getAnyActiveLeastConsumerLag() {
		List<String> groupIdList = getConsumerGroupList();
		return groupIdList.size() == 0 ? 0L : getConsumerGroupsLag(groupIdList);
	}

	// public static void main(String[] args) {
	// KafkaConfigReader config = new KafkaConfigReader();
	// ConsumerGroupTargetLag lag = new ConsumerGroupTargetLag<>(config);
	//
	// System.out.println(lag.getConsumerGroups());
	// System.out.println(lag.getConsumerGroupLag(lag.getConsumerGroups()));
	//
	// }

}
