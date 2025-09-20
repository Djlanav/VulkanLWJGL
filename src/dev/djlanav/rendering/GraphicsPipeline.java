package dev.djlanav.rendering;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

public class GraphicsPipeline {
	
	private Logger logger = LogManager.getLogger();
	
	private LongBuffer vertexBuffer = BufferUtils.createLongBuffer(1);
	private LongBuffer fragmentBuffer = BufferUtils.createLongBuffer(1);
	
	private LongBuffer pipelineLayout = BufferUtils.createLongBuffer(1);
	private LongBuffer graphicsPipeline = BufferUtils.createLongBuffer(1);
	private IntBuffer dynamicStates = BufferUtils.createIntBuffer(2);
	
	private VkViewport viewport = VkViewport.calloc();
	private VkViewport.Buffer viewports = VkViewport.calloc(1);
	
	private VkRect2D scissor = VkRect2D.calloc();
	private VkRect2D.Buffer scissors = VkRect2D.calloc(1);
	
	private VkPipelineShaderStageCreateInfo.Buffer shaderStageInfos = VkPipelineShaderStageCreateInfo.calloc(2);
	
	private VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc();
	private VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc();
	private VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc();
	private VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc();
	private VkPipelineRasterizationStateCreateInfo rasterizerState = VkPipelineRasterizationStateCreateInfo.calloc();
	private VkPipelineMultisampleStateCreateInfo multisamplingInfo = VkPipelineMultisampleStateCreateInfo.calloc();
	private VkPipelineColorBlendAttachmentState.Buffer colorBlendingInfo = VkPipelineColorBlendAttachmentState.calloc(1);
	private VkPipelineColorBlendStateCreateInfo colorBlendingState = VkPipelineColorBlendStateCreateInfo.calloc();
	private VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc();
	
	public GraphicsPipeline() {
		dynamicStates.put(0, VK10.VK_DYNAMIC_STATE_VIEWPORT);
		dynamicStates.put(1, VK10.VK_DYNAMIC_STATE_SCISSOR);
		initDynamicState();
	}
	
	public void initShader(VkDevice logicalDevice, Shader shader) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkShaderModuleCreateInfo shaderCreateInfo = VkShaderModuleCreateInfo.calloc(stack);
			shaderCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
			shaderCreateInfo.pCode(shader.getShaderBytes());
			
			switch (shader.getShaderType()) {
			case VERTEX:
				if (VK10.vkCreateShaderModule(logicalDevice, shaderCreateInfo, null, vertexBuffer) != VK10.VK_SUCCESS) {
					throw new RuntimeException("Failed to create vertex shader module");
				} else {
					logger.info("Created vertex shader module");
				}
				break;
			case FRAGMENT:
				if (VK10.vkCreateShaderModule(logicalDevice, shaderCreateInfo, null, fragmentBuffer) != VK10.VK_SUCCESS) {
					throw new RuntimeException("Failed to create fragment shader module");
				} else {
					logger.info("Created fragment shader module");
				}
				break;
			}
		}
	}
	
	public void createShaderStages() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			String entryName = "main\0";
			ByteBuffer shaderEntry = BufferUtils.createByteBuffer(entryName.length());
			shaderEntry.put(0, entryName.getBytes(StandardCharsets.UTF_8));
			
			VkPipelineShaderStageCreateInfo vertexInfo = VkPipelineShaderStageCreateInfo.calloc();
			vertexInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
			vertexInfo.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT);
			vertexInfo.module(vertexBuffer.get(0));
			vertexInfo.pName(shaderEntry);
			
			VkPipelineShaderStageCreateInfo fragInfo = VkPipelineShaderStageCreateInfo.calloc();
			fragInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
			fragInfo.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
			fragInfo.module(fragmentBuffer.get(0));
			fragInfo.pName(shaderEntry);
			
			shaderStageInfos.put(0, vertexInfo);
			shaderStageInfos.put(1, fragInfo);
			
			vertexInfo.free();
			fragInfo.free();
		}
	}
	
	public void createPipelineState() {
		vertexInput.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
		vertexInput.pVertexBindingDescriptions(null);
		vertexInput.pVertexAttributeDescriptions(null);
		
		inputAssembly.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
		inputAssembly.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
		inputAssembly.primitiveRestartEnable(false);
	}
	
	public void initViewport(VkExtent2D extent2D) {
		viewport.width((float) extent2D.width());
		viewport.height((float) extent2D.height());
		viewport.x(0);
		viewport.y(0);
		viewport.maxDepth(1.0f);
		viewport.minDepth(0.0f);
		viewports.put(0, viewport);
		
		viewportState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
		viewportState.viewportCount(1);
		viewportState.scissorCount(1);
		viewportState.pViewports(viewports);
	}
	
	public void initScissor(VkExtent2D extent2D) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkOffset2D offset = VkOffset2D.calloc(stack);
			offset.x(0);
			offset.y(0);
			
			scissor.offset(offset);
			scissor.extent(extent2D);
			scissors.put(0, scissor);
			viewportState.pScissors(scissors);
		}
	}
	
	public void initRasterizer() {
		rasterizerState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
		rasterizerState.depthClampEnable(false);
		rasterizerState.rasterizerDiscardEnable(false);
		rasterizerState.polygonMode(VK10.VK_POLYGON_MODE_FILL);
		rasterizerState.lineWidth(1.0f);
		rasterizerState.cullMode(VK10.VK_CULL_MODE_BACK_BIT);
		rasterizerState.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
		rasterizerState.depthBiasEnable(false);
		rasterizerState.depthBiasConstantFactor(0.0f);
		rasterizerState.depthBiasClamp(0.0f);
		rasterizerState.depthBiasSlopeFactor(0.0f);
	}
	
	public void setupMultisampling() {
		multisamplingInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
		multisamplingInfo.sampleShadingEnable(false);
		multisamplingInfo.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);
		multisamplingInfo.minSampleShading(1.0f);
		multisamplingInfo.pSampleMask(null);
		multisamplingInfo.alphaToCoverageEnable(false);
		multisamplingInfo.alphaToOneEnable(false);
	}
	
	public void setupColorBlending() {
		colorBlendingInfo.colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT 
				| VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);
		colorBlendingInfo.blendEnable(false);
		colorBlendingInfo.srcColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE);
		colorBlendingInfo.dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ZERO);
		colorBlendingInfo.colorBlendOp(VK10.VK_BLEND_OP_ADD);
		colorBlendingInfo.srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE);
		colorBlendingInfo.dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ZERO);
		colorBlendingInfo.alphaBlendOp(VK10.VK_BLEND_OP_ADD);
		
		colorBlendingState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
		colorBlendingState.logicOpEnable(false);
		colorBlendingState.logicOp(VK10.VK_LOGIC_OP_COPY);
		colorBlendingState.attachmentCount(1);
		colorBlendingState.pAttachments(colorBlendingInfo);
		colorBlendingState.blendConstants(0, 0.0f);
		colorBlendingState.blendConstants(1, 0.0f);
		colorBlendingState.blendConstants(2, 0.0f);
		colorBlendingState.blendConstants(3, 0.0f);
	}
	
	public void createPipelineLayout(VkDevice logicalDevice) {
		pipelineLayoutInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
		pipelineLayoutInfo.setLayoutCount(0);
		pipelineLayoutInfo.pSetLayouts(null);
		pipelineLayoutInfo.pPushConstantRanges(null);
		
		if (VK10.vkCreatePipelineLayout(logicalDevice, pipelineLayoutInfo, null, pipelineLayout) !=  VK10.VK_SUCCESS) {
			throw new RuntimeException("Failed to create pipeline layout");
		}
		
		logger.info("Created graphics pipeline layout");
	}
	
	public void createGraphicsPipeline(VkDevice logicalDevice, Renderer renderer) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkGraphicsPipelineCreateInfo.Buffer graphicsPipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
			graphicsPipelineInfo.sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
			graphicsPipelineInfo.stageCount(2);
			graphicsPipelineInfo.pStages(shaderStageInfos);
			graphicsPipelineInfo.pVertexInputState(vertexInput);
			graphicsPipelineInfo.pInputAssemblyState(inputAssembly);
			graphicsPipelineInfo.pViewportState(viewportState);
			graphicsPipelineInfo.pRasterizationState(rasterizerState);
			graphicsPipelineInfo.pMultisampleState(multisamplingInfo);
			graphicsPipelineInfo.pDepthStencilState(null);
			graphicsPipelineInfo.pColorBlendState(colorBlendingState);
			graphicsPipelineInfo.pDynamicState(dynamicState);
			graphicsPipelineInfo.layout(pipelineLayout.get(0));
			graphicsPipelineInfo.renderPass(renderer.getRenderPass());
			graphicsPipelineInfo.subpass(0);
			graphicsPipelineInfo.basePipelineHandle(MemoryUtil.NULL);
			graphicsPipelineInfo.basePipelineIndex(-1);
			
			if (VK10.vkCreateGraphicsPipelines(logicalDevice, 0, graphicsPipelineInfo, null, graphicsPipeline) != VK10.VK_SUCCESS) {
				throw new RuntimeException("Failed to create graphics pipeline");
			}
			
			dynamicState.free();
			shaderStageInfos.free();
			vertexInput.free();
			inputAssembly.free();
			viewportState.free();
			rasterizerState.free();
			multisamplingInfo.free();
			colorBlendingState.free();
			colorBlendingInfo.free();
			pipelineLayoutInfo.free();
			
			logger.info("Created graphics pipeline");
		}
	}
	
	public void cleanUp(VkDevice logicalDevice) {
		VK10.vkDestroyShaderModule(logicalDevice, vertexBuffer.get(0), null);
		logger.info("Destroyed vertex shader module");
		
		VK10.vkDestroyShaderModule(logicalDevice, fragmentBuffer.get(0), null);
		logger.info("Destroyed fragment shader module");
		
		VK10.vkDestroyPipeline(logicalDevice, graphicsPipeline.get(0), null);
		logger.info("Destroyed graphics pipeline");
		
		VK10.vkDestroyPipelineLayout(logicalDevice, pipelineLayout.get(0), null);
		logger.info("Destroyed pipeline layout");
		
		viewports.free();
		viewport.free();
		scissors.free();
		scissor.free();
	}
	
	private void initDynamicState() {
		dynamicState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
		dynamicState.pDynamicStates(dynamicStates);
	}
	
	public long getGraphicsPipeline() {
		if (graphicsPipeline != null) {
			return graphicsPipeline.get(0);
		} else {
			throw new NullPointerException("Graphics Pipeline LongBuffer is null");
		}
	}
	
	public VkViewport.Buffer getViewportsBuffer() {
		return viewports;
	}
	
	public VkRect2D.Buffer getScissorsBuffer() {
		return scissors;
	}
}
