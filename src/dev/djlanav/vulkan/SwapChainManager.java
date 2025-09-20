package dev.djlanav.vulkan;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import dev.djlanav.main.WindowManager; 

public class SwapChainManager {
	
	private Logger logger = LogManager.getLogger();

	private VkExtent2D extent2D = VkExtent2D.malloc();
	private VkSurfaceCapabilitiesKHR surfaceCaps = VkSurfaceCapabilitiesKHR.calloc();
	
	private VkSurfaceFormatKHR.Buffer surfaceFormats;
	private IntBuffer presentModes;
	private LongBuffer swapchainImages;
	private ArrayList<LongBuffer> imageViews = new ArrayList<LongBuffer>();
	
	private VkSurfaceFormatKHR mainSurfaceFormat;
	
	private long swapChain;
	private int surfaceFormatsCount;
	private int presentModesCount;
	private int imageCount;
	
	private boolean hasSurfaceCaps = false;
	
	public void querySwapChainSupport(VkPhysicalDevice physicalDevice, VulkanManager vulkanManager) {
		long surface = vulkanManager.getVkSurface();
		if (KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfaceCaps) != VK10.VK_SUCCESS) {
			throw new RuntimeException("Failed to get physical device surface capabilities");
		}
		hasSurfaceCaps = true;
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer formatCountBuffer = stack.callocInt(1);
			IntBuffer presentModeCountBuffer = stack.callocInt(1);
			
			if (KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCountBuffer, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get physical device surface format count");
			}
			
			int surfaceFormatsCount = formatCountBuffer.get(0);
			surfaceFormats = VkSurfaceFormatKHR.calloc(surfaceFormatsCount);
			if (KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCountBuffer, surfaceFormats) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get physical device surface formats");
			}
			
			if (KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCountBuffer, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get physical device present modes count");
			}
			
			int presentModesCount = presentModeCountBuffer.get(0);
			presentModes = BufferUtils.createIntBuffer(presentModesCount);
			if (KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCountBuffer, presentModes) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get physical device present modes");
			}
			
			this.surfaceFormatsCount = surfaceFormatsCount;
			this.presentModesCount = presentModesCount;
		}
	}
	
	public void createSwapChain(VkSwapchainCreateInfoKHR createInfo, VkDevice device) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer swapchainBuffer = stack.callocLong(1);
			if (KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, null, swapchainBuffer) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create Vulkan swap chain");
			}
			
			swapChain = swapchainBuffer.get(0);
		}
	}
	
	public void createSwapchainImages(VkDevice logicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer imageCountBuffer = stack.callocInt(1);
			if (KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapChain, imageCountBuffer, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get swapchain image count");
			}
			
			imageCount = imageCountBuffer.get(0);
			swapchainImages = BufferUtils.createLongBuffer(imageCount);
			
			if (KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice, swapChain, imageCountBuffer, swapchainImages) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to get swapchain images");
			}
			
			logger.info("Got swapchain images. Image count: " + imageCount);
			
			for (int i = 0; i < imageCount; i++) {
				VkImageViewCreateInfo imageInfo = VkImageViewCreateInfo.calloc(stack);
				imageInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
				imageInfo.image(swapchainImages.get(i));
				imageInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
				imageInfo.format(mainSurfaceFormat.format());
				
				imageInfo.components().r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
				imageInfo.components().g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
				imageInfo.components().b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
				imageInfo.components().a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
				
				imageInfo.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
				imageInfo.subresourceRange().baseMipLevel(0);
				imageInfo.subresourceRange().levelCount(1);
				imageInfo.subresourceRange().baseArrayLayer(0);
				imageInfo.subresourceRange().layerCount(1);
				
				LongBuffer imageView = BufferUtils.createLongBuffer(1);
				if (VK10.vkCreateImageView(logicalDevice, imageInfo, null, imageView) != VK10.VK_SUCCESS) {
					throw new RuntimeException("Failed to create image view");
				}
				
				imageViews.add(imageView);
			}
		}
	}
	
	public VkSurfaceFormatKHR chooseSwapSurfaceFormat() {
		for (int i = 0; i < surfaceFormatsCount; i++) {
			VkSurfaceFormatKHR surfaceFormat = surfaceFormats.get(i);
			int format = surfaceFormat.format();
			int colorSpace = surfaceFormat.colorSpace();
			
			if (format == VK10.VK_FORMAT_B8G8R8A8_SRGB && colorSpace == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
				mainSurfaceFormat = surfaceFormat;
				return surfaceFormat;
			}
		}
		
		VkSurfaceFormatKHR defaultFormat = surfaceFormats.get(0);
		mainSurfaceFormat = defaultFormat;
		return defaultFormat;
	}
	
	public int chooseSwapPresentMode() {
		for (int i = 0; i < presentModesCount; i++) {
			int presentMode = presentModes.get(i);
			
			if (presentMode == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
				return presentMode;
			}
		}
		
		return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
	}
	
	public VkExtent2D chooseSwapExtent() {
		if (!hasSurfaceCaps) {
			throw new IllegalStateException("Surface capabilities not initialized");
		}
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer width = stack.callocInt(1);
			IntBuffer height = stack.callocInt(1);
			GLFW.glfwGetFramebufferSize(WindowManager.getWindow(), width, height);
			
			int clampedWidth = Math.clamp(width.get(0), surfaceCaps.minImageExtent().width(), surfaceCaps.maxImageExtent().width());
			int clampedHeight = Math.clamp(height.get(0), surfaceCaps.minImageExtent().height(), surfaceCaps.maxImageExtent().height());
			extent2D.width(clampedWidth);
			extent2D.height(clampedHeight);
			
			return extent2D;
		}
	}
	
	public boolean checkSwapChainSupport() {
		return surfaceFormats.hasRemaining() && presentModes.hasRemaining();
	}
	
	public void cleanUp(VkDevice logicalDevice) {
		for (LongBuffer imageView : imageViews) {
			VK10.vkDestroyImageView(logicalDevice, imageView.get(0), null);
		}
		logger.info("Destroyed image views");
		
		KHRSwapchain.vkDestroySwapchainKHR(logicalDevice, swapChain, null);
		logger.info("Destroyed Swapchain");
		
		surfaceCaps.free();
		surfaceFormats.free();
		extent2D.free();
	}
	
	public boolean checkSurfaceCaps() {
		return surfaceCaps == null;
	}
	
	public boolean checkSurfaceFormats() {
		return surfaceFormats.hasRemaining();
	}
	
	public boolean checkPresentModes() {
		return presentModes.hasRemaining();
	}
	
	public VkSurfaceCapabilitiesKHR getSurfaceCaps() {
		return surfaceCaps;
	}
	
	public VkSurfaceFormatKHR.Buffer getSurfaceFormats() {
		return surfaceFormats;
	}
	
	public IntBuffer getPresentModes() {
		return presentModes;
	}

	public int getSurfaceFormatsCount() {
		return surfaceFormatsCount;
	}

	public int getPresentModesCount() {
		return presentModesCount;
	}

	public int getImageCount() {
		return imageCount;
	}
	
	public long getSwapChain() {
		return swapChain;
	}

	public VkExtent2D getExtent2D() {
		return extent2D;
	}

	public VkSurfaceFormatKHR getMainSurfaceFormat() {
		return mainSurfaceFormat;
	}
	
	public ArrayList<LongBuffer> getImageViews() {
		return imageViews;
	}
}
