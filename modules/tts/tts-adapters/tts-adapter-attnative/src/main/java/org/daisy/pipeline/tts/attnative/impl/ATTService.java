package org.daisy.pipeline.tts.attnative.impl;

import java.util.Map;

import javax.sound.sampled.AudioFormat;

import org.daisy.pipeline.tts.AbstractTTSService;
import org.daisy.pipeline.tts.RoundRobinLoadBalancer;
import org.daisy.pipeline.tts.TTSEngine;
import org.daisy.pipeline.tts.TTSService;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.ComponentContext;

@Component(
	name = "nativeatt-tts-service",
	service = { TTSService.class }
)
public class ATTService extends AbstractTTSService {
	
	@Activate
	protected void loadSSMLadapter() {
		super.loadSSMLadapter("/transform-ssml.xsl", ATTService.class);
	}

	@Override
	public TTSEngine newEngine(Map<String, String> params) throws Throwable {

		System.loadLibrary("att");
		
		// settings
		int priority = convertToInt(params, "org.daisy.pipeline.tts.att.priority", 10);

		// load balancer
		String serversProperty = "org.daisy.pipeline.tts.att.servers";
		String serverVal = params.get(serversProperty);
		if (serverVal == null) {
			serverVal = "localhost:8888";
		}
		RoundRobinLoadBalancer balancer;
		try {
			balancer = new RoundRobinLoadBalancer(serverVal, this);
		} catch (Exception e) {
			throw new SynthesisException("invalid value for property " + serversProperty);
		}
		
		return new ATTEngine(this, balancer, priority);
	}

	@Override
	public String getName() {
		return "att";
	}

	@Override
	public String getVersion() {
		return "native";
	}

	private static int convertToInt(Map<String, String> params, String prop, int defaultVal)
	        throws SynthesisException {
		String str = params.get(prop);
		if (str != null) {
			try {
				defaultVal = Integer.valueOf(str);
			} catch (NumberFormatException e) {
				throw new SynthesisException(str + " is not a valid a value for property "
				        + prop);
			}
		}
		return defaultVal;
	}

}
