import bpy

# create UV shader mix node group, credits to @FriesFX
uvm = bpy.data.node_groups.get("UV Shader Mix")

if not uvm:
    uvm = bpy.data.node_groups.new(name="UV Shader Mix", type="ShaderNodeTree")
    # for node in tex_shader.nodes: tex_shader.nodes.remove(node)

    mix_1 = uvm.nodes.new("ShaderNodeMixShader")
    mix_2 = uvm.nodes.new("ShaderNodeMixShader")
    mix_3 = uvm.nodes.new("ShaderNodeMixShader")
    mix_4 = uvm.nodes.new("ShaderNodeMixShader")
    mix_1.location = [-500, 300]
    mix_2.location = [-300, 300]
    mix_3.location = [-100, 300]
    mix_4.location = [100, 300]
    uvm.links.new(mix_1.outputs[0], mix_2.inputs[1])
    uvm.links.new(mix_2.outputs[0], mix_3.inputs[1])
    uvm.links.new(mix_3.outputs[0], mix_4.inputs[1])

    x = -1700
    y = 700
    sep = uvm.nodes.new("ShaderNodeSeparateRGB")
    sep.location = [x + 200, y - 200]

    m1_1 = uvm.nodes.new("ShaderNodeMath")
    m1_2 = uvm.nodes.new("ShaderNodeMath")
    m1_3 = uvm.nodes.new("ShaderNodeMath")
    m1_1.location = [x + 400, y]
    m1_2.location = [x + 400, y - 200]
    m1_3.location = [x + 400, y - 400]
    m1_1.operation = "LESS_THAN"
    m1_2.operation = "LESS_THAN"
    m1_3.operation = "LESS_THAN"
    m1_1.inputs[1].default_value = 1.420
    m1_2.inputs[1].default_value = 1.720
    m1_3.inputs[1].default_value = 3.000
    uvm.links.new(sep.outputs[0], m1_1.inputs[0])
    uvm.links.new(sep.outputs[0], m1_2.inputs[0])
    uvm.links.new(sep.outputs[0], m1_3.inputs[0])

    add_1_2 = uvm.nodes.new("ShaderNodeMath")
    add_1_2.location = [x + 600, y - 300]
    add_1_2.operation = "ADD"
    uvm.links.new(m1_1.outputs[0], add_1_2.inputs[0])
    uvm.links.new(m1_2.outputs[0], add_1_2.inputs[1])

    m2_1 = uvm.nodes.new("ShaderNodeMath")
    m2_2 = uvm.nodes.new("ShaderNodeMath")
    m2_3 = uvm.nodes.new("ShaderNodeMath")
    m2_4 = uvm.nodes.new("ShaderNodeMath")
    m2_1.location = [x + 800, y]
    m2_2.location = [x + 800, y - 200]
    m2_3.location = [x + 800, y - 400]
    m2_4.location = [x + 800, y - 600]
    m2_1.operation = "ADD"
    m2_2.operation = "SUBTRACT"
    m2_3.operation = "SUBTRACT"
    m2_4.operation = "LESS_THAN"
    m2_1.use_clamp = True
    m2_2.use_clamp = True
    m2_3.use_clamp = True
    m2_4.use_clamp = True
    m2_1.inputs[1].default_value = 0
    m2_4.inputs[1].default_value = 0.700
    uvm.links.new(m1_1.outputs[0], m2_1.inputs[0])
    uvm.links.new(m1_2.outputs[0], m2_2.inputs[0])
    uvm.links.new(m1_1.outputs[0], m2_2.inputs[1])
    uvm.links.new(m1_3.outputs[0], m2_3.inputs[0])
    uvm.links.new(add_1_2.outputs[0], m2_3.inputs[1])
    uvm.links.new(m1_3.outputs[0], m2_4.inputs[0])

    uvm.links.new(m2_1.outputs[0], mix_1.inputs[0])
    uvm.links.new(m2_2.outputs[0], mix_4.inputs[0])
    uvm.links.new(m2_3.outputs[0], mix_2.inputs[0])
    uvm.links.new(m2_4.outputs[0], mix_3.inputs[0])

    # I/O
    g_in = uvm.nodes.new("NodeGroupInput")
    g_out = uvm.nodes.new("NodeGroupOutput")
    g_in.location = [-1700, 220]
    g_out.location = [300, 300]
    uvm.links.new(g_in.outputs[0], sep.inputs[0])
    uvm.links.new(g_in.outputs[1], mix_1.inputs[2])
    uvm.links.new(g_in.outputs[2], mix_2.inputs[2])
    uvm.links.new(g_in.outputs[3], mix_3.inputs[2])
    uvm.links.new(g_in.outputs[4], mix_4.inputs[2])
    uvm.links.new(mix_4.outputs[0], g_out.inputs[0])

# create texture shader node group, credits to @Lucas7yoshi
tex_shader = bpy.data.node_groups.get("Texture Shader")

if not tex_shader:
    tex_shader = bpy.data.node_groups.new(name="Texture Shader", type="ShaderNodeTree")
    # for node in tex_shader.nodes: tex_shader.nodes.remove(node)

    g_in = tex_shader.nodes.new("NodeGroupInput")
    g_out = tex_shader.nodes.new("NodeGroupOutput")
    g_in.location = [-700, 0]
    g_out.location = [350, 300]

    principled_bsdf = tex_shader.nodes.new(type="ShaderNodeBsdfPrincipled")
    principled_bsdf.location = [50, 300]
    tex_shader.links.new(principled_bsdf.outputs[0], g_out.inputs[0])

    # diffuse
    tex_shader.links.new(g_in.outputs[0], principled_bsdf.inputs["Base Color"])

    # normal
    norm_y = -1
    norm_curve = tex_shader.nodes.new("ShaderNodeRGBCurve")
    norm_map = tex_shader.nodes.new("ShaderNodeNormalMap")
    norm_curve.location = [-500, norm_y]
    norm_map.location = [-200, norm_y]
    norm_curve.mapping.curves[1].points[0].location = [0, 1]
    norm_curve.mapping.curves[1].points[1].location = [1, 0]
    tex_shader.links.new(g_in.outputs[1], norm_curve.inputs[1])
    tex_shader.links.new(norm_curve.outputs[0], norm_map.inputs[1])
    tex_shader.links.new(norm_map.outputs[0], principled_bsdf.inputs["Normal"])
    tex_shader.inputs[1].default_value = [0.5, 0.5, 1, 1]

    # specular
    spec_y = 140
    spec_separate_rgb = tex_shader.nodes.new("ShaderNodeSeparateRGB")
    spec_separate_rgb.location = [-200, spec_y]
    tex_shader.links.new(g_in.outputs[2], spec_separate_rgb.inputs[0])
    tex_shader.links.new(spec_separate_rgb.outputs[0], principled_bsdf.inputs["Specular"])
    tex_shader.links.new(spec_separate_rgb.outputs[1], principled_bsdf.inputs["Metallic"])
    tex_shader.links.new(spec_separate_rgb.outputs[2], principled_bsdf.inputs["Roughness"])
    tex_shader.inputs[2].default_value = [0.5, 0, 0.5, 1]

    # emission
    tex_shader.links.new(g_in.outputs[3], principled_bsdf.inputs["Emission"])
    tex_shader.inputs[3].default_value = [0, 0, 0, 1]

    # alpha
    alpha_separate_rgb = tex_shader.nodes.new("ShaderNodeSeparateRGB")
    alpha_separate_rgb.location = [-200, -180]
    tex_shader.links.new(g_in.outputs[4], alpha_separate_rgb.inputs[0])
    tex_shader.links.new(alpha_separate_rgb.outputs[0], principled_bsdf.inputs["Alpha"])
    tex_shader.inputs[4].default_value = [1, 0, 0, 1]

    tex_shader.inputs[0].name = "Diffuse"
    tex_shader.inputs[1].name = "Normal"
    tex_shader.inputs[2].name = "Specular"
    tex_shader.inputs[3].name = "Emission"
    tex_shader.inputs[4].name = "Alpha"