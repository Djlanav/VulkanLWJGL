package dev.djlanav.vulkan;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

public class DeviceManager {

	private VkPhysicalDevice physicalDevice;
	private VkDevice logicalDevice;
	private VkPhysicalDeviceProperties physicalProperties;
	private VkPhysicalDeviceFeatures physicalFeatures;

	private SwapChainManager swapManager;
	private QueueFamilyManager qfm;

	private Logger logger = LogManager.getLogger();
	private boolean enableValidationLayers = true;
	private String[] validationLayers = { "VK_LAYER_KHRONOS_validation" };
	private String[] logicalDeviceExtensions = { 
			KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
			KHRPipelineLibrary.VK_KHR_PIPELINE_LIBRARY_EXTENSION_NAME,
			EXTGraphicsPipelineLibrary.VK_EXT_GRAPHICS_PIPELINE_LIBRARY_EXTENSION_NAME,
	};

	public DeviceManager(QueueFamilyManager qfm, SwapChainManager swapManager) {
		this.qfm = qfm;
		this.swapManager = swapManager;
	}

	public void createPhysicalDevice(VkInstance instance) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer deviceCountBuffer = stack.callocInt(1);

			if (VK10.vkEnumeratePhysicalDevices(instance, deviceCountBuffer, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to enumerate physical device count!");
			}

			int deviceCount = deviceCountBuffer.get(0);
			PointerBuffer deviceBuffer = stack.callocPointer(deviceCount);
			
			if (VK10.vkEnumeratePhysicalDevices(instance, deviceCountBuffer, deviceBuffer) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to enumerate physical devices!");
			}

			boolean deviceFound = false;
			for (int i = 0; i < deviceCount; i++) {
				long deviceHandle = deviceBuffer.get(i);
				VkPhysicalDevice device = new VkPhysicalDevice(deviceHandle, instance);
				
				if (isPhysicalDeviceSuitable(device, stack)) {
					logger.info("Found suitable device: " + physicalProperties.deviceNameString());
					setPhysicalDevice(device);
					
					deviceFound = true;
					break;
				}
			}
			
			if (!deviceFound) {
				throw new RuntimeException("Failed to find a suitable device");
			}
		}
	}

	public void createLogicalDevice(QueueFamilyManager familyManager) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkDeviceQueueCreateInfo.Buffer queuesInfo = createQueues(stack);
			PointerBuffer extBuffer = createExtensionsBuffer(stack);
			
			VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack);
			deviceInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
			deviceInfo.pQueueCreateInfos(queuesInfo);
			deviceInfo.pEnabledFeatures(physicalFeatures);
			deviceInfo.ppEnabledExtensionNames(extBuffer);
			
			if (enableValidationLayers) {
				Optional<PointerBuffer> bufferOptional = checkValidationLayers(stack);

				if (!bufferOptional.isEmpty()) {
					PointerBuffer buffer = bufferOptional.get();
					deviceInfo.ppEnabledLayerNames(buffer);
				}
			}

			PointerBuffer devicePointer = stack.callocPointer(1);
			if (VK10.vkCreateDevice(physicalDevice, deviceInfo, null, devicePointer) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create device");
			} else {
				logger.info("Successfully created logical device");
			}
			
			VkDevice device = new VkDevice(devicePointer.get(0), physicalDevice, deviceInfo);
			if (isLogicalDeviceSuitable(device, stack)) {
				logger.info("Logical device is suitable for use");
			} else {
				throw new RuntimeException("Failed to create a suitable logical device");
			}
			
			setLogicalDevice(device);
			
			ArrayList<VulkanQueue> vulkanQueues = qfm.getQueues();
			
			int queueFamiliesSize = vulkanQueues.size();
			PointerBuffer queuesBuffer = stack.callocPointer(queueFamiliesSize);
			
			for (VulkanQueue queue : vulkanQueues) {
				int queueFamily = queue.getParentFamilyIndex();
				int queueIndex = queue.getQueueIndex();
				
				VK10.vkGetDeviceQueue(logicalDevice, queueFamily, queueIndex, queuesBuffer);
				
				long handle = queuesBuffer.get(queueFamily);
				if (handle == 0) {
					throw new NullPointerException("Queue handle is null");
				}
				
				queue.initQueue(device, handle);
				logger.info("Got device queue in queue family " + queueFamily);
			}
		}
	}
	
	private VkDeviceQueueCreateInfo.Buffer createQueues(MemoryStack stack) {
		ArrayList<VulkanQueue> vulkanQueues = qfm.getQueues();
		int queuesLength = vulkanQueues.size();
		
		float[] priorities = { 1.0f };
		FloatBuffer prioritiesBuffer = BufferUtils.createFloatBuffer(priorities.length);
		prioritiesBuffer.put(0, priorities);

		VkDeviceQueueCreateInfo.Buffer queuesBuffer = VkDeviceQueueCreateInfo.calloc(queuesLength, stack);
		
		VulkanQueue previousQueue = null;
		for (int i = 0; i < queuesLength; i++) {
			VulkanQueue queue = vulkanQueues.get(i);
			
			VkDeviceQueueCreateInfo queueCreateInfo = VkDeviceQueueCreateInfo.calloc(stack);
			queueCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
			queueCreateInfo.pQueuePriorities(prioritiesBuffer);
			queueCreateInfo.queueFamilyIndex(queue.getParentFamilyIndex());

			if (previousQueue != null && queue.getParentFamilyIndex() != previousQueue.getParentFamilyIndex()) {
				queueCreateInfo.queueFamilyIndex(queue.getParentFamilyIndex());
				logger.debug("Queue family index: " + queue.getParentFamilyIndex());
			} else if (previousQueue != null && queue.getParentFamilyIndex() == previousQueue.getParentFamilyIndex()) {
				logger.debug("Queue info with index " + previousQueue.getParentFamilyIndex() + " already exists. Skipping");
				continue;
			}
			
			queuesBuffer.put(i, queueCreateInfo);
			previousQueue = vulkanQueues.get(i);
		}

		return queuesBuffer;
	}
	
	private boolean checkExtensionSupport(VkPhysicalDevice device, MemoryStack stack) {
		IntBuffer extCountBuffer = stack.callocInt(1);
		if (VK10.vkEnumerateDeviceExtensionProperties(device, "", extCountBuffer, null) != VK10.VK_SUCCESS) {
			logger.error("Failed to get the number of physical device extensions");
		}
		
		int extCount = extCountBuffer.get(0);
		VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(extCount, stack);
		if (VK10.vkEnumerateDeviceExtensionProperties(device, "", extCountBuffer, extensions) != VK10.VK_SUCCESS) {
			logger.error("Failed to get physical device extensions");
		}

		String[] foundExtensions = new String[3];
		for (int x = 0; x < logicalDeviceExtensions.length; x++) {
			String search = logicalDeviceExtensions[x];
			
			for (int y = 0; y < extCount; y++) {
				VkExtensionProperties props = extensions.get(y);
				String extName = props.extensionNameString();
				
				if (search.equals(extName)) {
					logger.info("Found extension: " + extName);
					foundExtensions[x] = extName;
					
					break;
				}
			}
		}
		
		if (Arrays.equals(foundExtensions, logicalDeviceExtensions)) {
			return true;
		} else {
			return false;
		}
	}

	private Optional<PointerBuffer> checkValidationLayers(MemoryStack stack) {
		IntBuffer countBuffer = stack.callocInt(1);

		if (VK10.vkEnumerateDeviceLayerProperties(physicalDevice, countBuffer, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException("Failed to enumerate device layer properties");
		}

		int count = countBuffer.get(0);
		VkLayerProperties.Buffer layerPropsBuffer = VkLayerProperties.calloc(count, stack);

		if (VK10.vkEnumerateDeviceLayerProperties(physicalDevice, countBuffer, layerPropsBuffer) != VK10.VK_SUCCESS) {
			throw new RuntimeException("Failed to write layer data to layer properties buffer");
		}

		for (int x = 0; x < validationLayers.length; x++) {
			String layerSearch = validationLayers[x];

			for (int y = 0; y < count; y++) {
				VkLayerProperties layerProperties = layerPropsBuffer.get(y);
				String layerName = layerProperties.layerNameString();

				if (layerSearch.equals(layerName)) {
					logger.debug("Found validation layer: " + layerName);

					PointerBuffer buffer = createLayerNameBuffer(stack);
					return Optional.of(buffer);
				}
			}
		}

		return Optional.empty();
	}
	
	private PointerBuffer createLayerNameBuffer(MemoryStack stack) {
		PointerBuffer buffer = stack.mallocPointer(validationLayers.length);
		
		for (int i = 0; i < validationLayers.length; i++) {
			String layer = validationLayers[i];
			buffer.put(i, stack.ASCII(layer));
		}
		return buffer;
	}
	
	private PointerBuffer createExtensionsBuffer(MemoryStack stack) {
		int length = logicalDeviceExtensions.length;
		PointerBuffer buffer = stack.mallocPointer(length);
		
		for (int i = 0; i < length; i++) {
			String extension = logicalDeviceExtensions[i];
			buffer.put(i, stack.ASCII(extension));
		}
		
		return buffer;
	}
	
	@SuppressWarnings("unused")
	private void getVulkanlogicalDeviceExtensions() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer countBuffer = stack.callocInt(1);
			VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, "", countBuffer, null);
			
			VkExtensionProperties.Buffer extensionPropertiesBuffer = VkExtensionProperties.calloc(countBuffer.get(0), stack);
			VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, "", countBuffer, extensionPropertiesBuffer);
			
			for (int i = 0; i < countBuffer.get(0); i++) {
				VkExtensionProperties extension = extensionPropertiesBuffer.get(i);
				logger.debug("Extension: " + extension.extensionNameString());
			}
		}
	}

	private boolean isPhysicalDeviceSuitable(VkPhysicalDevice physDevice, MemoryStack stack) {
		physicalProperties = VkPhysicalDeviceProperties.malloc();
		physicalFeatures = VkPhysicalDeviceFeatures.malloc();

		VK10.vkGetPhysicalDeviceProperties(physDevice, physicalProperties);
		VK10.vkGetPhysicalDeviceFeatures(physDevice, physicalFeatures);

		return physicalProperties.deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU 
				&& checkExtensionSupport(physDevice, stack);
	}
	
	public void cleanUp() {
		VK10.vkDestroyDevice(logicalDevice, null);
		logger.info("Destroyed logical device");
		
		physicalProperties.free();
		physicalFeatures.free();
	}
	
	private boolean isLogicalDeviceSuitable(VkDevice device, MemoryStack stack) {
		return swapManager.checkSwapChainSupport();
	}

	public VkPhysicalDevice getPhysicalDevice() {
		return physicalDevice;
	}

	public void setPhysicalDevice(VkPhysicalDevice physicalDevice) {
		this.physicalDevice = physicalDevice;
	}

	public VkDevice getLogicalDevice() {
		return logicalDevice;
	}

	public void setLogicalDevice(VkDevice logicalDevice) {
		this.logicalDevice = logicalDevice;
	}

	public VkPhysicalDeviceFeatures getPhysicalFeatures() {
		return physicalFeatures;
	}

	public void setPhysicalFeatures(VkPhysicalDeviceFeatures physicalFeatures) {
		this.physicalFeatures = physicalFeatures;
	}

	public String[] getlogicalDeviceExtensions() {
		return logicalDeviceExtensions;
	}

	public void setlogicalDeviceExtensions(String[] logicalDeviceExtensions) {
		this.logicalDeviceExtensions = logicalDeviceExtensions;
	}

	public SwapChainManager getSwapManager() {
		return swapManager;
	}
}
