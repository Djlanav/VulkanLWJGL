package dev.djlanav.main;

import java.nio.IntBuffer;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import dev.djlanav.vulkan.QueueFamilyManager;
import dev.djlanav.vulkan.SwapChainManager;
import dev.djlanav.rendering.GraphicsPipeline;
import dev.djlanav.rendering.Renderer;
import dev.djlanav.rendering.ShaderManager;
import dev.djlanav.rendering.ShaderType;
import dev.djlanav.vulkan.DeviceManager;
import dev.djlanav.vulkan.VulkanManager;
import dev.djlanav.vulkan.VulkanQueue;

public class Loader {

	private VulkanManager vulkanManager = new VulkanManager();
	private SwapChainManager swapChainManager = new SwapChainManager();
	private QueueFamilyManager queueFamilyManager = new QueueFamilyManager();
	private DeviceManager deviceManager = new DeviceManager(queueFamilyManager, swapChainManager);
	private ShaderManager shaderManager = new ShaderManager();
	private Renderer renderer = new Renderer();
	private GraphicsPipeline graphicsPipeline = new GraphicsPipeline();
	
	private Logger logger = LogManager.getLogger();
	
	public long createWindow() {
		WindowManager.initWindow(800, 600, "Vulkan LWJGL");
		return WindowManager.getWindow();
	}
	
	public void initVulkan() {
		vulkanManager.createVulkanInstance("Trident");
		vulkanManager.createVulkanSurface(WindowManager.getWindow());
		
		deviceManager.createPhysicalDevice(vulkanManager.getVkInstance());
		swapChainManager.querySwapChainSupport(deviceManager.getPhysicalDevice(), vulkanManager);
		
		queueFamilyManager.findQueueFamilies(deviceManager.getPhysicalDevice(), vulkanManager.getVkSurface());
		deviceManager.createLogicalDevice(queueFamilyManager);
		VkDevice logicalDevice = deviceManager.getLogicalDevice();
		
		createSwapChain();
		swapChainManager.createSwapchainImages(logicalDevice);
		createGraphicsPipeline();
		
		renderer.createFramebuffer(logicalDevice, swapChainManager);
		renderer.createCommandPool(logicalDevice, queueFamilyManager);
		renderer.createCommandBuffer(logicalDevice);
		renderer.createSyncObjects(logicalDevice);
	}
	
	private void createSwapChain() {
		VkSurfaceCapabilitiesKHR surfaceCaps = swapChainManager.getSurfaceCaps();
		
		VkSurfaceFormatKHR surfaceFormat = swapChainManager.chooseSwapSurfaceFormat();
		int presentMode = swapChainManager.chooseSwapPresentMode();
		VkExtent2D extent2D = swapChainManager.chooseSwapExtent();
		
		int imageCount = surfaceCaps.minImageCount() + 1;
		if (surfaceCaps.maxImageCount() > 0 && imageCount > surfaceCaps.maxImageCount()) {
			imageCount = surfaceCaps.maxImageCount();
			logger.debug("Swap chain image count: " + imageCount);
		}
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSwapchainCreateInfoKHR swapChainInfo = VkSwapchainCreateInfoKHR.calloc(stack);
			swapChainInfo.sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
			swapChainInfo.surface(vulkanManager.getVkSurface());
			swapChainInfo.minImageCount(imageCount);
			swapChainInfo.imageFormat(surfaceFormat.format());
			swapChainInfo.imageColorSpace(surfaceFormat.colorSpace());
			swapChainInfo.imageExtent(extent2D);
			swapChainInfo.imageArrayLayers(1);
			swapChainInfo.imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
			swapChainInfo.preTransform(surfaceCaps.currentTransform());
			swapChainInfo.compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
			swapChainInfo.presentMode(presentMode);
			swapChainInfo.clipped(true);
			swapChainInfo.oldSwapchain(MemoryUtil.NULL);
			
			ArrayList<VulkanQueue> queues = queueFamilyManager.getQueues();
			int[] familyIndices = queueFamilyManager.createQueueFamilyIndicesArray();
			IntBuffer familyIndicesBuffer = BufferUtils.createIntBuffer(familyIndices.length);
			familyIndicesBuffer.put(0, familyIndices);
			
			int queuesSize = queues.size();
			int extraI = -1;
			
			for (int i = 0; i < queuesSize; i++) {
				extraI += 1;
				
				if (!(extraI > queuesSize)) {
					VulkanQueue nextQueue = queues.get(extraI);
					VulkanQueue firstQueue = queues.get(i);
					
					if (firstQueue.getParentFamilyIndex() != nextQueue.getParentFamilyIndex()) {
						swapChainInfo.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT);
						swapChainInfo.queueFamilyIndexCount(2);
						swapChainInfo.pQueueFamilyIndices(familyIndicesBuffer);
						break;
					} else {
						swapChainInfo.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
						break;
					}
				}
			}
			
			swapChainManager.createSwapChain(swapChainInfo, deviceManager.getLogicalDevice());
		}
	}
	
	public void createGraphicsPipeline() {
		VkDevice logicalDevice = deviceManager.getLogicalDevice();
		VkExtent2D extent2D = swapChainManager.getExtent2D();
		
		shaderManager.loadCompiledShader(ShaderType.VERTEX);
		shaderManager.loadCompiledShader(ShaderType.FRAGMENT);
		
		graphicsPipeline.initShader(logicalDevice, shaderManager.getMainVertex());
		graphicsPipeline.initShader(logicalDevice, shaderManager.getMainFragment());
		graphicsPipeline.createShaderStages();
		graphicsPipeline.createPipelineState();
		graphicsPipeline.initViewport(extent2D);
		graphicsPipeline.initScissor(extent2D);
		graphicsPipeline.initRasterizer();
		graphicsPipeline.setupMultisampling();
		graphicsPipeline.setupColorBlending();
		graphicsPipeline.createPipelineLayout(logicalDevice);
		renderer.createRenderPass(logicalDevice, swapChainManager.getMainSurfaceFormat());
		graphicsPipeline.createGraphicsPipeline(logicalDevice, renderer);
	}
	
	public void renderingSetup() {
		VkDevice logicalDevice = deviceManager.getLogicalDevice();
		
		renderer.createFramebuffer(logicalDevice, swapChainManager);
		renderer.createFramebuffer(logicalDevice, swapChainManager);
		renderer.createCommandPool(logicalDevice, queueFamilyManager);
		renderer.createCommandBuffer(logicalDevice);
	}
	
	public void cleanUp() {
		renderer.cleanUp(deviceManager.getLogicalDevice());
		graphicsPipeline.cleanUp(deviceManager.getLogicalDevice());
		swapChainManager.cleanUp(deviceManager.getLogicalDevice());
		deviceManager.cleanUp();
		vulkanManager.cleanUp();
	}
	
	public Renderer getRenderer() {
		return renderer;
	}

	public SwapChainManager getSwapChainManager() {
		return swapChainManager;
	}

	public void setSwapChainManager(SwapChainManager swapChainManager) {
		this.swapChainManager = swapChainManager;
	}

	public QueueFamilyManager getQueueFamilyManager() {
		return queueFamilyManager;
	}

	public void setQueueFamilyManager(QueueFamilyManager queueFamilyManager) {
		this.queueFamilyManager = queueFamilyManager;
	}

	public DeviceManager getDeviceManager() {
		return deviceManager;
	}

	public void setDeviceManager(DeviceManager deviceManager) {
		this.deviceManager = deviceManager;
	}

	public GraphicsPipeline getGraphicsPipeline() {
		return graphicsPipeline;
	}

	public void setGraphicsPipeline(GraphicsPipeline graphicsPipeline) {
		this.graphicsPipeline = graphicsPipeline;
	}
}
