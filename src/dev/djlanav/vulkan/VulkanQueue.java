package dev.djlanav.vulkan;

import java.util.EnumSet;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;

public class VulkanQueue {

	private VkQueue queue;
	private EnumSet<QueueType> queueCapabilities;
	private int queueIndex;
	private int parentFamilyIndex;
	
	public VulkanQueue(EnumSet<QueueType> queueCapabilities, int queueIndex, int parentFamilyIndex) {
		this.queueCapabilities = queueCapabilities;
		this.queueIndex = queueIndex;
		this.parentFamilyIndex = parentFamilyIndex;
	}
	
	public void initQueue(VkDevice logicalDevice, long handle) {
		queue = new VkQueue(handle, logicalDevice);
	}
	
	public VkQueue getQueue() {
		return queue;
	}
	
	public int getQueueIndex() {
		return queueIndex;
	}
	
	public int getParentFamilyIndex() {
		return parentFamilyIndex;
	}
	
	public EnumSet<QueueType> getQueueCapabilities() {
		return queueCapabilities;
	}
	
	public void setQueueCapabilities(EnumSet<QueueType> queueCapabilities) {
		this.queueCapabilities = queueCapabilities;
	}
}
