package com.os.filamenttriangle

import android.animation.ValueAnimator
import android.opengl.Matrix
import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.os.filamenttriangle.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            Filament.init()
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper
    private lateinit var choreographer: Choreographer
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private lateinit var material: Material
    private lateinit var vertexBuffer: VertexBuffer
    private lateinit var indexBuffer: IndexBuffer

    @Entity
    private var renderable = 0

    @Entity
    private var light = 0
    private var swapChain: SwapChain? = null
    private val frameScheduler = FrameCallback()
    private val animator = ValueAnimator.ofFloat(0.0f, 360.0f)


    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(this)

        setupSurfaceView()
        setupFilament()
        setupView()
        setupScene()
    }

    private fun setupSurfaceView() {
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(binding.surfaceView)
    }

    private fun setupFilament() {
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())
    }

    private fun setupView() {
        scene.skybox = Skybox.Builder().color(0.035f, 0.035f, 0.035f, 1.0f).build(engine)
        view.camera = camera
        view.scene = scene
    }

    private fun setupScene() {
        loadMaterial()
        createMesh()

        renderable = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.01f))
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 3)
            .material(0, material.defaultInstance).build(engine, renderable)
        scene.addEntity(renderable)

        light = EntityManager.get().create()

        val (r, g, b) = Colors.cct(5_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(r, g, b)
            .intensity(110_000.0f)
            .direction(0.0f, -0.5f, -1.0f).castShadows(true).build(engine, light)
        scene.addEntity(light)
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        camera.lookAt(0.0, 3.0, 4.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

        startAnimation()
        //trianglePosition()
    }

    private fun loadMaterial() {
        readUncompressedAsset("materials/baked_color.filamat").let {
            material = Material.Builder().payload(it, it.remaining()).build(engine)
        }
    }

    private fun createMesh() {
        val intSize = 4
        val floatSize = 4
        val shortSize = 2

        val vertexSize = 3 * floatSize + intSize

        data class Vertex(val x: Float, val y: Float, val z: Float, val color: Int)

        fun ByteBuffer.put(v: Vertex): ByteBuffer {
            putFloat(v.x)
            putFloat(v.y)
            putFloat(v.z)
            putInt(v.color)
            return this
        }

        val vertexCount = 3
        val a1 = PI * 2.0 / 3.0
        val a2 = PI * 4.0 / 3.0

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            .order(ByteOrder.nativeOrder()).put(Vertex(1.0f, 0.0f, 0.0f, 0xffff0000.toInt()))
            .put(Vertex(cos(a1).toFloat(), sin(a1).toFloat(), 0.0f, 0xff00ff00.toInt()))
            .put(Vertex(cos(a2).toFloat(), sin(a2).toFloat(), 0.0f, 0xff0000ff.toInt()))
            .flip()

        vertexBuffer = VertexBuffer.Builder().bufferCount(1).vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexAttribute.COLOR, 0, AttributeType.UBYTE4, 3 * floatSize, vertexSize)
            .normalized(VertexAttribute.COLOR).build(engine)

        vertexBuffer.setBufferAt(engine, 0, vertexData)


        val indexData =
            ByteBuffer.allocate(vertexCount * shortSize).order(ByteOrder.nativeOrder()).putShort(0)
                .putShort(1).putShort(2).flip()

        indexBuffer =
            IndexBuffer.Builder().indexCount(3).bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine)
        indexBuffer.setBuffer(engine, indexData)
    }


    private fun startAnimation() {
        animator.interpolator = LinearInterpolator()
        animator.duration = 4000
        animator.repeatMode = ValueAnimator.RESTART
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            val transformMatrix = FloatArray(16)
            override fun onAnimationUpdate(a: ValueAnimator) {
                Matrix.setRotateM(transformMatrix, 0, -(a.animatedValue as Float), 0.0f, 0.0f, 1.0f)
                val tcm = engine.transformManager
                tcm.setTransform(tcm.getInstance(renderable), transformMatrix)
            }
        })
        animator.start()
    }

    private fun trianglePosition() {
        val transformMatrix = FloatArray(16)
        Matrix.setRotateM(transformMatrix, 0, 90f, 0.0f, 0.0f, 1.0f)
        val tcm = engine.transformManager
        tcm.setTransform(tcm.getInstance(renderable), transformMatrix)
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
        animator.start()
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()


        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
        uiHelper.detach()
        engine.destroyEntity(renderable)
        engine.destroyRenderer(renderer)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterial(material)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        val entityManager = EntityManager.get()
        entityManager.destroy(renderable)
        entityManager.destroy(camera.entity)
        engine.destroy()
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (uiHelper.isReadyToRender) {
                if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }
        }
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            displayHelper.attach(renderer, binding.surfaceView.display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            val zoom = 1.5
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(
                Camera.Projection.ORTHO, -aspect * zoom, aspect * zoom, -zoom, zoom, 0.0, 10.0
            )
            view.viewport = Viewport(0, 0, width, height)
        }
    }

    private fun readUncompressedAsset(assetName: String): ByteBuffer {
        assets.openFd(assetName).use { fd ->
            val input = fd.createInputStream()
            val dst = ByteBuffer.allocate(fd.length.toInt())
            val src = Channels.newChannel(input)
            src.read(dst)
            src.close()
            return dst.apply { rewind() }
        }
    }
}