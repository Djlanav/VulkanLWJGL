package dev.djlanav.vulkan;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

public class QueueFamilyManager {

	private ArrayList<VulkanQueue> queues = new ArrayList<VulkanQueue>();
	
	private Logger logger = LogManager.getLogger();
	
	public void findQueueFamilies(VkPhysicalDevice physicalDevice, long surface) {
		int queuesFound = 0x00;
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer queueFamilyCountBuffer = stack.callocInt(1);
			IntBuffer surfaceSupportBuffer = stack.callocInt(1);
			
			VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, null);
			
			int queueFamilyCount = queueFamilyCountBuffer.get(0);
			VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount, stack);
			
			VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, queueFamilies);
			
			logger.info("Queue Family Count: " + queueFamilyCount);			
			for (int i = 0; i < queueFamilyCount; i++) {
				if (queuesFound == 0xAB) {
					logger.info("Found needed queues");
					break;
				}
				
				VkQueueFamilyProperties queueFamily = queueFamilies.get(i);
				KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, surfaceSupportBuffer);
				
				int surfaceSupport = surfaceSupportBuffer.get(0);
				int graphicsFlag = queueFamily.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT;
				
				if (surfaceSupport == VK10.VK_TRUE) {
					queues.add(i, new VulkanQueue(EnumSet.of(QueueType.PRESENTATION), i, i));
					logger.info("Found presentation capable queue family");
					queuesFound |= 0xA;
				}
				
				// TODO: The 0xB check here might cause extra checks. I should fix that at some point.
				if ((queueFamily.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) == graphicsFlag && queuesFound != 0xB) {
					if (!(i >= queues.size())) {
						VulkanQueue queue = queues.get(i);
						
						if (queue.getQueueCapabilities().contains(QueueType.PRESENTATION)) {
							queue.setQueueCapabilities(EnumSet.of(QueueType.GRAPHICS, QueueType.PRESENTATION));
							logger.info("Presentation  queue family also has graphics capabilities");
							
							queuesFound = (queuesFound << 4) | 0xB;
							continue;
						}	
					}
					
					queues.add(new VulkanQueue(EnumSet.of(QueueType.GRAPHICS), i, i));
					logger.info("Found graphics capable queue family");
					queuesFound = (queuesFound << 4) | 0xB;
				}
			}
		}
	}
	
	public int[] createQueueFamilyIndicesArray() {
		int[] queueFamilyIndices = new int[queues.size()];
		for (int i = 0; i < queues.size(); i++) {
			VulkanQueue queue = queues.get(i);
			queueFamilyIndices[i] = queue.getParentFamilyIndex();
		}
		
		return queueFamilyIndices;
	}

	public ArrayList<VulkanQueue> getQueues() {
		return queues;
	}
}
