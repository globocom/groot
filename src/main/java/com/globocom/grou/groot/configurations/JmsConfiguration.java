/*
 * Copyright (c) 2017-2017 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.groot.configurations;

import com.globocom.grou.groot.SystemEnv;
import org.apache.activemq.artemis.api.core.client.loadbalance.RoundRobinConnectionLoadBalancingPolicy;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import static org.springframework.jms.core.JmsTemplate.RECEIVE_TIMEOUT_NO_WAIT;

@Configuration
@EnableJms
public class JmsConfiguration {

    @Bean(name="jmsConnectionFactory")
    public CachingConnectionFactory cachingConnectionFactory() throws JMSException {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(SystemEnv.BROKER_CONN.getValue());
        connectionFactory.setUser(SystemEnv.BROKER_USER.getValue());
        connectionFactory.setPassword(SystemEnv.BROKER_PASS.getValue());
        if (Boolean.valueOf(SystemEnv.BROKER_HA.getValue())) {
            connectionFactory.setConnectionLoadBalancingPolicyClassName(RoundRobinConnectionLoadBalancingPolicy.class.getName());
        }
        cachingConnectionFactory.setTargetConnectionFactory(connectionFactory);
        cachingConnectionFactory.setSessionCacheSize(100);
        cachingConnectionFactory.setCacheConsumers(true);
        return cachingConnectionFactory;
    }

    @Bean(name = "jmsTemplate")
    public JmsTemplate jmsTemplate(@Value("#{jmsConnectionFactory}") ConnectionFactory connectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setExplicitQosEnabled(true);
        jmsTemplate.setDeliveryPersistent(false);
        jmsTemplate.setReceiveTimeout(RECEIVE_TIMEOUT_NO_WAIT);
        jmsTemplate.setTimeToLive(Long.parseLong(SystemEnv.JMS_TIMEOUT.getValue()));
        return jmsTemplate;
    }

}
