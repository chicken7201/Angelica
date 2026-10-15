#version 460 core
uniform sampler2D source;
uniform ivec4 sourceLevel;
layout(location = 0) out vec4 converted;
// Converts the selected source mip to a storage-readable color value.
void main() { converted = texelFetch(source, ivec2(gl_FragCoord.xy), sourceLevel.x); }
