package dev.djlanav.rendering;

import java.nio.ByteBuffer;

public class Shader {

	private ShaderType shaderType;
	private ByteBuffer sprvBytes;

	public Shader(ShaderType shaderType) {
		this.shaderType = shaderType;
	}

	public ByteBuffer getShaderBytes() {
		return sprvBytes;
	}
	
	public ShaderType getShaderType() {
		return shaderType;
	}
	
	public void setShaderBytes(ByteBuffer sprvBytes) {
		this.sprvBytes = sprvBytes;
	}
}
