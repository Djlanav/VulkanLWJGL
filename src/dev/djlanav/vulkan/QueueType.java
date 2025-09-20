package dev.djlanav.vulkan;

import java.util.EnumSet;

public enum QueueType {
	GRAPHICS,
	PRESENTATION;
	
	public static final EnumSet<QueueType> QUEUE_CAPABILITIES = EnumSet.allOf(QueueType.class);
}
