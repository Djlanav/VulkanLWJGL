package dev.djlanav.vulkan;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Optional;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import dev.djlanav.main.Main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.*;

public class VulkanManager {

	private VkInstance vkInstance;
	private long vkSurface;
	
	private HashMap<String, Integer> queueFamilyMap = new HashMap<String, Integer>();
	private Logger logger = LogManager.getLogger();
	
	private String[] validationLayers = { "VK_LAYER_KHRONOS_validation" };

	public void createVulkanInstance(String engineName) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer engineNameBuffer = stack.UTF8(engineName);
			ByteBuffer appNameBuffer = stack.UTF8("Vulkan LWJGL");

			VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);
			appInfo.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO);
			appInfo.apiVersion(VK10.VK_MAKE_API_VERSION(1, 4, 304, 0));
			appInfo.engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0));
			appInfo.applicationVersion(VK10.VK_MAKE_VERSION(0, 0, 1));
			appInfo.pEngineName(engineNameBuffer);
			appInfo.pApplicationName(appNameBuffer);

			IntBuffer propCountBuffer = stack.callocInt(1);
			if (VK10.vkEnumerateInstanceExtensionProperties((String) null, propCountBuffer, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to enumerate instance extension properties");	
			}

			int propCount = propCountBuffer.get(0);
			var propBuffer = VkExtensionProperties.calloc(propCount, stack);
			if (VK10.vkEnumerateInstanceExtensionProperties((String) null, propCountBuffer, propBuffer) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get extension properties");	
			}

			VkInstanceCreateInfo instanceInfo = VkInstanceCreateInfo.calloc(stack);
			instanceInfo.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
			instanceInfo.pApplicationInfo(appInfo);

			PointerBuffer glfwExtensions = getExtensions();
			instanceInfo.ppEnabledExtensionNames(glfwExtensions);

			if (Main.debugMode) {
				Optional<PointerBuffer> bufferOptional = getValidationLayerSupport(stack);
				if (!bufferOptional.isEmpty()) {
					PointerBuffer buffer = bufferOptional.get();
					instanceInfo.ppEnabledLayerNames(buffer);
					
					logger.info("Enabled instance validation layers");
				}
			}

			PointerBuffer instancePointer = stack.mallocPointer(1);
			int instanceResult = VK10.vkCreateInstance(instanceInfo, null, instancePointer);

			if (instanceResult != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create Vulkan instance");
			} else {
				logger.info("Successfully created Vulkan instance!");
			}

			setVkInstance(new VkInstance(instancePointer.get(0), instanceInfo));
		}
	}
	
	public void createVulkanSurface(long window) {
		if (vkInstance == null || vkInstance.address() == 0) {
			throw new IllegalStateException("Cannot create surfaces. Vulkan instance is not initialized.");
		}
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer surfaceBuffer = stack.callocLong(1);
			if (GLFWVulkan.glfwCreateWindowSurface(vkInstance, window, null, surfaceBuffer) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create window surface");
			}
			
			setVkSurface(surfaceBuffer.get(0));
			logger.info("Successfully created Vulkan surface");
		}
	}

	private Optional<PointerBuffer> getValidationLayerSupport(MemoryStack stack) {
		IntBuffer layerPropsCountBuffer = stack.callocInt(1);
		if (VK10.vkEnumerateInstanceLayerProperties(layerPropsCountBuffer, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException("Failed to enumerate instance layer properties!");	
		}

		int layerPropsCount = layerPropsCountBuffer.get(0);
		VkLayerProperties.Buffer layerPropsArray = VkLayerProperties.calloc(layerPropsCount, stack);
		
		if (VK10.vkEnumerateInstanceLayerProperties(layerPropsCountBuffer, layerPropsArray) != VK10.VK_SUCCESS) {
			throw new RuntimeException("Failed to enumerate instance layer properties!");	
		}

		for (int x = 0; x < validationLayers.length; x++) {
			String layerSearch = validationLayers[x];

			for (int y = 0; y < layerPropsCount; y++) {
				VkLayerProperties layerProps = layerPropsArray.get(y);
				String layerName = layerProps.layerNameString();

				if (layerSearch.equals(layerName)) {
					logger.debug("Found validation layer: " + layerName);

					PointerBuffer buffer = createLayerNameBuffer(stack);
					return Optional.of(buffer);
				}
			}
		}

		return Optional.empty();
	}

	private PointerBuffer getExtensions() {
		PointerBuffer buffer = GLFWVulkan.glfwGetRequiredInstanceExtensions();
		if (buffer == null) {
			throw new RuntimeException("Failed to get required instance extensions from GLFW");
		}

		return buffer;
	}

	private PointerBuffer createLayerNameBuffer(MemoryStack stack) {
		PointerBuffer buffer = stack.mallocPointer(validationLayers.length);
		String layer = validationLayers[0];

		buffer.put(stack.ASCII(layer));
		buffer.flip();
		return buffer;
	}

	public void cleanUp() {
		KHRSurface.vkDestroySurfaceKHR(vkInstance, vkSurface, null);
		logger.info("Destroyed surface");
		
		VK10.vkDestroyInstance(vkInstance, null);
		logger.info("Destroyed Vulkan instance");
	}

	public VkInstance getVkInstance() {
		return vkInstance;
	}

	public void setVkInstance(VkInstance vkInstance) {
		this.vkInstance = vkInstance;
	}

	public HashMap<String, Integer> getQueueFamilyMap() {
		return queueFamilyMap;
	}

	public void setQueueFamilyMap(HashMap<String, Integer> queueFamilyMap) {
		this.queueFamilyMap = queueFamilyMap;
	}

	public long getVkSurface() {
		return vkSurface;
	}

	public void setVkSurface(long vkSurface) {
		this.vkSurface = vkSurface;
	}
}
