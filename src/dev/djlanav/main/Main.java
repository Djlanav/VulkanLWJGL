package dev.djlanav.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.*;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;

import dev.djlanav.rendering.GraphicsPipeline;
import dev.djlanav.rendering.Renderer;
import dev.djlanav.vulkan.QueueFamilyManager;
import dev.djlanav.vulkan.SwapChainManager;

public class Main {
	
	public static boolean debugMode = true;
	private static Logger logger = LogManager.getLogger();
	
	private Loader loader = new Loader();
	
	private void run() {
		long window = loader.createWindow();
		
		GLFW.glfwSetKeyCallback(WindowManager.getWindow(), (windowHandle, key, scancode, action, mods) -> {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				GLFW.glfwSetWindowShouldClose(windowHandle, true);
			}
		});
		
		loader.initVulkan();
		
		Renderer renderer = loader.getRenderer();
		VkDevice logicalDevice = loader.getDeviceManager().getLogicalDevice();
		SwapChainManager swapchainManager = loader.getSwapChainManager();
		QueueFamilyManager queueFamilyManager = loader.getQueueFamilyManager();
		GraphicsPipeline graphicsPipeline = loader.getGraphicsPipeline();
		
		double lastTime = GLFW.glfwGetTime();
		int frames = 0;
		
		while (!GLFW.glfwWindowShouldClose(window) ) {
			double currentTime = GLFW.glfwGetTime();
			frames++;
			
			if (currentTime - lastTime >= 1.0) {
				System.out.println("FPS: " + frames);
				WindowManager.updateWindowTitle("Vulkan LWJGL | FPS: " + frames);
				frames = 0;
				lastTime = currentTime;
			}
			
			renderer.render(logicalDevice, swapchainManager, queueFamilyManager, graphicsPipeline);
			GLFW.glfwPollEvents();
		}
		VK10.vkDeviceWaitIdle(logicalDevice);
		
		GLFW.glfwSetKeyCallback(window, null).free();
	}

	private void cleanUp() {
		WindowManager.cleanUp();
		loader.cleanUp();
	}
	
	public static void main(String[] args) {
		if (debugMode) {
			logger.debug("Debug mode enabled");
			System.setProperty("org.lwjgl.util.Debug", String.valueOf(true));
			System.setProperty("org.lwjgl.util.DebugAllocator", String.valueOf(true));
			System.setProperty("org.lwjgl.util.DebugStack", String.valueOf(true));
		}
		
		Main main = new Main();
		
		main.run();
		main.cleanUp();
	}
}
