package dev.djlanav.rendering;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import dev.djlanav.vulkan.*;

public class Renderer {
	
	private Logger logger = LogManager.getLogger();
	
	private VkAttachmentDescription.Buffer colorAttachments = VkAttachmentDescription.calloc(1);
	private VkAttachmentReference.Buffer referenceAttachments = VkAttachmentReference.calloc(1);
	private VkSubpassDescription.Buffer subpassBuffer = VkSubpassDescription.calloc(1);
	private VkSubpassDependency.Buffer dependenciesBuffer = VkSubpassDependency.calloc(1);
	
	private LongBuffer renderPass = BufferUtils.createLongBuffer(1);
	private ArrayList<LongBuffer> frameBuffers = new ArrayList<LongBuffer>();
	
	private LongBuffer commandPool = BufferUtils.createLongBuffer(1);
	private PointerBuffer commandBuffers = BufferUtils.createPointerBuffer(1);
	private VkCommandBuffer commandBuffer;
	private LongBuffer imageAvailableSempahore = BufferUtils.createLongBuffer(1);
	private LongBuffer renderingFinishedSemaphore = BufferUtils.createLongBuffer(1);
	private LongBuffer inFlightFence = BufferUtils.createLongBuffer(1);
	private IntBuffer imageIndexBuffer = BufferUtils.createIntBuffer(1);
	
	public void recordCommandBuffer() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkCommandBufferBeginInfo cmdBegin = VkCommandBufferBeginInfo.calloc(stack);
			cmdBegin.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
			cmdBegin.flags(0);
			cmdBegin.pInheritanceInfo(null);
			
			if (VK10.vkBeginCommandBuffer(commandBuffer, cmdBegin) != VK10.VK_SUCCESS) {
				logger.error("Failed to begin command buffer");
			}
		}
	}
	
	public void prepareRender(SwapChainManager swapchainManager, int imageIndex, GraphicsPipeline graphicsPipeline) {
		recordCommandBuffer();
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkOffset2D offset = VkOffset2D.calloc(stack);
			
			float[] colorValues = { 0.0f, 0.4f, 0.8f, 1.0f };
			FloatBuffer colorsBuffer = BufferUtils.createFloatBuffer(4);
			colorsBuffer.put(0, colorValues);
			
			VkClearColorValue clearColorValue = VkClearColorValue.calloc(stack);
			clearColorValue.float32(colorsBuffer);
			
			VkClearValue.Buffer clearBuffer = VkClearValue.calloc(1, stack);
			VkClearValue clearColor = VkClearValue.calloc(stack);
			clearColor.color(clearColorValue);
			clearBuffer.put(0, clearColor);
			
			VkRenderPassBeginInfo renderPassBegin = VkRenderPassBeginInfo.calloc(stack);
			renderPassBegin.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
			renderPassBegin.renderPass(renderPass.get(0));
			renderPassBegin.framebuffer(frameBuffers.get(imageIndex).get(0));
			renderPassBegin.renderArea().offset(offset);
			renderPassBegin.renderArea().extent(swapchainManager.getExtent2D());
			renderPassBegin.clearValueCount(1);
			renderPassBegin.pClearValues(clearBuffer);
			
			VK10.vkCmdBeginRenderPass(commandBuffer, renderPassBegin, VK10.VK_SUBPASS_CONTENTS_INLINE);;
			
			VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline.getGraphicsPipeline());
			
			VK10.vkCmdSetViewport(commandBuffer, 0, graphicsPipeline.getViewportsBuffer());
			VK10.vkCmdSetScissor(commandBuffer, 0, graphicsPipeline.getScissorsBuffer());
			
			VK10.vkCmdDraw(commandBuffer, 3, 1, 0, 0);
			VK10.vkCmdEndRenderPass(commandBuffer);
			
			if (VK10.vkEndCommandBuffer(commandBuffer) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to end command buffer");
			}
		}
	}
	
	public void render(VkDevice logicalDevice, SwapChainManager swapchainManager, QueueFamilyManager queueFamilyManager, GraphicsPipeline graphicsPipeline) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VK10.vkWaitForFences(logicalDevice, inFlightFence.get(0), true, Long.MAX_VALUE);
			VK10.vkResetFences(logicalDevice, inFlightFence.get(0));
			
			KHRSwapchain.vkAcquireNextImageKHR(logicalDevice, swapchainManager.getSwapChain(), Long.MAX_VALUE, imageAvailableSempahore.get(0), MemoryUtil.NULL, imageIndexBuffer);
			VK10.vkResetCommandBuffer(commandBuffer, 0);
			
			int imageIndex = imageIndexBuffer.get(0);
			prepareRender(swapchainManager, imageIndex, graphicsPipeline);
			
			IntBuffer waitStages = stack.callocInt(1);
			waitStages.put(0, VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
			
			VkSubmitInfo.Buffer submitInfos = VkSubmitInfo.calloc(1, stack);
			VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
			submitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
			submitInfo.waitSemaphoreCount(1);
			submitInfo.pWaitSemaphores(imageAvailableSempahore);
			submitInfo.pWaitDstStageMask(waitStages);
			submitInfo.pCommandBuffers(commandBuffers);
			submitInfo.pSignalSemaphores(renderingFinishedSemaphore);
			submitInfos.put(0, submitInfo);
			
			for (VulkanQueue queue : queueFamilyManager.getQueues()) {
				if (queue.getQueueCapabilities().contains(QueueType.GRAPHICS)) {
					
					// TODO: If validation layers are enabled this causes a memory access violation in the dll
					if (VK10.vkQueueSubmit(queue.getQueue(), submitInfo, inFlightFence.get(0)) != VK10.VK_SUCCESS) {
						logger.error("Failed to submit draw command buffer");
					}
				}
			}
			
			LongBuffer swapchainBuffer = stack.callocLong(1);
			IntBuffer imageIndices = stack.callocInt(1);
			
			swapchainBuffer.put(0, swapchainManager.getSwapChain());
			imageIndices.put(0, imageIndex);
			
			VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
			presentInfo.sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
			presentInfo.pWaitSemaphores(renderingFinishedSemaphore);
			presentInfo.swapchainCount(1);
			presentInfo.pSwapchains(swapchainBuffer);
			presentInfo.pImageIndices(imageIndices);
			presentInfo.pResults(null);
			
			for (VulkanQueue queue : queueFamilyManager.getQueues()) {
				if (queue.getQueueCapabilities().contains(QueueType.PRESENTATION)) {
					if (KHRSwapchain.vkQueuePresentKHR(queue.getQueue(), presentInfo) != VK10.VK_SUCCESS) {
						logger.error("Could not present image from swapchain");
					}
				}
			}
		}
	}
	
	public void createRenderPass(VkDevice logicalDevice, VkSurfaceFormatKHR surfaceFormat) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			createAttachments(stack, surfaceFormat);
			createAttachmentRefs(stack);
			createSubpasses(stack);
			subpassBuffer.free();
			referenceAttachments.free();
			
			VkSubpassDependency subpassDependency = VkSubpassDependency.calloc(stack);
			subpassDependency.srcSubpass(VK10.VK_SUBPASS_EXTERNAL);
			subpassDependency.dstSubpass(0);
			subpassDependency.srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
			subpassDependency.srcAccessMask(0);
			subpassDependency.dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
			subpassDependency.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
			dependenciesBuffer.put(0, subpassDependency);
			
			VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
			renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
			renderPassInfo.pAttachments(colorAttachments);
			renderPassInfo.pSubpasses(subpassBuffer);
			renderPassInfo.pDependencies(dependenciesBuffer);
			
			if (VK10.vkCreateRenderPass(logicalDevice, renderPassInfo, null, renderPass) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create render pass");
			}
			
			dependenciesBuffer.free();
			colorAttachments.free();
			logger.info("Created render pass");
		}
	}
	
	private void createAttachments(MemoryStack stack, VkSurfaceFormatKHR surfaceFormat) {
		VkAttachmentDescription colorAttachment = VkAttachmentDescription.calloc(stack);
		colorAttachment.format(surfaceFormat.format());
		colorAttachment.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
		
		colorAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
		colorAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
		
		colorAttachment.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
		colorAttachment.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
		
		colorAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
		colorAttachment.finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
		colorAttachments.put(0, colorAttachment);	
	}
	
	private void createAttachmentRefs(MemoryStack stack) {
		VkAttachmentReference attachmentRef = VkAttachmentReference.calloc(stack);
		attachmentRef.attachment(0);
		attachmentRef.layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
		referenceAttachments.put(0, attachmentRef);
	}
	
	private void createSubpasses(MemoryStack stack) {
		VkSubpassDescription subPass = VkSubpassDescription.calloc(stack);
		subPass.pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS);
		subPass.colorAttachmentCount(1);
		subPass.pColorAttachments(referenceAttachments);
		subpassBuffer.put(0, subPass);
	}
	
	public void createFramebuffer(VkDevice logicalDevice, SwapChainManager swapchainManager) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ArrayList<LongBuffer> imageViews = swapchainManager.getImageViews();
			VkExtent2D extent2D = swapchainManager.getExtent2D();
			
			for (LongBuffer imageView : imageViews) {
				VkFramebufferCreateInfo frameBufferInfo = VkFramebufferCreateInfo.calloc(stack);
				frameBufferInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
				frameBufferInfo.renderPass(renderPass.get(0));
				frameBufferInfo.attachmentCount(1);
				frameBufferInfo.pAttachments(imageView);
				frameBufferInfo.width(extent2D.width());
				frameBufferInfo.height(extent2D.height());
				frameBufferInfo.layers(1);
				
				LongBuffer frameBuffer = BufferUtils.createLongBuffer(1);
				if (VK10.vkCreateFramebuffer(logicalDevice, frameBufferInfo, null, frameBuffer) != VK10.VK_SUCCESS) {
					throw new RuntimeException("Failed to create framebuffer");
				}
				
				frameBuffers.add(frameBuffer);
			}
		}
	}
	
	public void createCommandPool(VkDevice logicalDevice, QueueFamilyManager queueFamilyManager) {
		VulkanQueue vulkanQueue = null;
		for (VulkanQueue queue : queueFamilyManager.getQueues()) {
			if (queue.getQueueCapabilities().contains(QueueType.GRAPHICS)) {
				vulkanQueue = queue;
				break;
			}
		}
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkCommandPoolCreateInfo cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack);
			cmdPoolInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
			cmdPoolInfo.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
			cmdPoolInfo.queueFamilyIndex(vulkanQueue.getParentFamilyIndex());
			
			if (VK10.vkCreateCommandPool(logicalDevice, cmdPoolInfo, null, commandPool) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create Vulkan command pool");
			}
		}
	}
	
	public void createCommandBuffer(VkDevice logicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkCommandBufferAllocateInfo cmdBufferInfo = VkCommandBufferAllocateInfo.calloc(stack);
			cmdBufferInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
			cmdBufferInfo.commandPool(commandPool.get(0));
			cmdBufferInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
			cmdBufferInfo.commandBufferCount(1);
			
			if (VK10.vkAllocateCommandBuffers(logicalDevice, cmdBufferInfo, commandBuffers) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to allocate command buffers");
			}
			
			commandBuffer = new VkCommandBuffer(commandBuffers.get(0), logicalDevice);
		}
	}
	
	public void createSyncObjects(VkDevice logicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSemaphoreCreateInfo imageAvailableInfo = VkSemaphoreCreateInfo.calloc(stack);
			imageAvailableInfo.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
			
			VkSemaphoreCreateInfo renderingFinishedInfo = VkSemaphoreCreateInfo.calloc(stack);
			renderingFinishedInfo.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
			
			VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
			fenceInfo.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
			fenceInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
			
			if (VK10.vkCreateSemaphore(logicalDevice, imageAvailableInfo, null, imageAvailableSempahore) != VK10.VK_SUCCESS ||
				VK10.vkCreateSemaphore(logicalDevice, renderingFinishedInfo, null, renderingFinishedSemaphore) != VK10.VK_SUCCESS ||
				VK10.vkCreateFence(logicalDevice, fenceInfo, null, inFlightFence) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create sync objects");
			}
		}
	}
	
	public void cleanUp(VkDevice logicalDevice) {
		for (LongBuffer frameBuffer : frameBuffers) {
			VK10.vkDestroyFramebuffer(logicalDevice, frameBuffer.get(0), null);
		}
		logger.info("Destroyed framebuffers");
		
		VK10.vkDestroyRenderPass(logicalDevice, renderPass.get(0), null);
		logger.info("Destroyed render pass");
		
		VK10.vkDestroyCommandPool(logicalDevice, commandPool.get(0), null);
		logger.info("Destroyed command pool and command buffers");
		
		VK10.vkDestroySemaphore(logicalDevice, imageAvailableSempahore.get(0), null);
		VK10.vkDestroySemaphore(logicalDevice, renderingFinishedSemaphore.get(0), null);
		VK10.vkDestroyFence(logicalDevice, inFlightFence.get(0), null);
		logger.info("Destroyed sync objects");
	}
	
	public long getRenderPass() {
		return renderPass.get(0);
	}
	
	public VkCommandBuffer getCommandBuffer() {
		return commandBuffer;
	}
}
