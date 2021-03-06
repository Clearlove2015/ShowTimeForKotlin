package com.example.happyghost.showtimeforkotlin.ui.book.read

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.*


import android.graphics.Point
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDelegate
import android.view.KeyEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import com.example.happyghost.showtimeforkotlin.AppApplication

import com.example.happyghost.showtimeforkotlin.R
import com.example.happyghost.showtimeforkotlin.RxBus.event.DownloadEvent
import com.example.happyghost.showtimeforkotlin.RxBus.event.DownloadMessageEvent
import com.example.happyghost.showtimeforkotlin.RxBus.event.DownloadProgressEvent
import com.example.happyghost.showtimeforkotlin.RxBus.event.ReadEvent
import com.example.happyghost.showtimeforkotlin.adapter.bookadapter.BookMarkAdapter
import com.example.happyghost.showtimeforkotlin.adapter.bookadapter.ReadThemeAdapter
import com.example.happyghost.showtimeforkotlin.bean.bookdate.BookMark
import com.example.happyghost.showtimeforkotlin.bean.bookdate.BookMixATocBean
import com.example.happyghost.showtimeforkotlin.bean.bookdate.ChapterReadBean
import com.example.happyghost.showtimeforkotlin.bean.bookdate.Recommend
import com.example.happyghost.showtimeforkotlin.downloadservice.DownloadBookService
import com.example.happyghost.showtimeforkotlin.inject.component.bookcomponent.DaggerReadComponent
import com.example.happyghost.showtimeforkotlin.inject.module.bookmodule.ReadModule
import com.example.happyghost.showtimeforkotlin.ui.base.BaseActivity
import com.example.happyghost.showtimeforkotlin.ui.book.bookdetail.BookDetailInfoActivity
import com.example.happyghost.showtimeforkotlin.utils.*
import com.example.happyghost.showtimeforkotlin.wegit.SelectCricleImageView
import com.example.happyghost.showtimeforkotlin.wegit.read.OnReadStateChangeListener
import com.example.happyghost.showtimeforkotlin.wegit.read.OverlappedWidget
import com.example.happyghost.showtimeforkotlin.wegit.read.PageWidget
import com.example.happyghost.showtimeforkotlin.wegit.read.ReadView
import com.nineoldandroids.animation.ObjectAnimator
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.activity_read.*
import kotlinx.android.synthetic.main.layout_read_aa_set.*
import kotlinx.android.synthetic.main.layout_read_mark.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.text.SimpleDateFormat
import java.util.*

class ReadActivity : BaseActivity<ReadPresenter>(),IReadView, View.OnClickListener {

    private var statusBarColor: Int = 0
    var mBookId :String = ""
    var mIsFromSd :Boolean  =false
    var currentChapter = 1
    var chapters = ArrayList<BookMixATocBean.MixTocBean.ChaptersBean>()
    lateinit var mPageWidget: ReadView
    var statusBarHeight : Int =-1
    /**
     * 是否开始阅读章节
     */
    private var startRead = false
    var curTheme: Int = 0
    private val sdf = SimpleDateFormat("HH:mm")
    private var receiver = Receiver()
    private val intentFilter = IntentFilter()
    var bookBean: Recommend.RecommendBooks? = null
    var title:String = ""


    override fun loadBookToc(list: List<BookMixATocBean.MixTocBean.ChaptersBean>) {
        chapters.clear()
        chapters.addAll(list)
        if(mIsFromSd){
            doAsync {
                mPresenter.insertBooks(mBookId,list)
            }
        }
        loadCurrentChapter()
    }

    /**
     * 获取当前章节。章节文件存在则直接阅读，不存在则请求
     */
    fun loadCurrentChapter(){
        if(FileUtils.hasChapterFile(mBookId,currentChapter)!=null){
            loadChapterRead(null,currentChapter)
        }else{
            mPresenter.getChapterRead(chapters.get(currentChapter-1).link!!,currentChapter)
        }

    }

    @Synchronized override fun loadChapterRead(data: ChapterReadBean.Chapter?, chapter: Int) {
        if(data!=null){
            FileUtils.saveChapterFile(mBookId,chapter,data)
        }
        if(!startRead){
            startRead=true
            currentChapter = chapter
            if(!mPageWidget.isPrepared){
                mPageWidget.initRead(curTheme)
            }else{
                mPageWidget.jumpToChapter(currentChapter)
            }
        }
    }

    override fun upDataView() {
        if(mIsFromSd){
            val chapter = BookMixATocBean.MixTocBean.ChaptersBean()
            chapter.title = bookBean?.title
            chapters.add(chapter)
            //加载章节内容
            loadChapterRead(null, currentChapter)
            //本地书籍隐藏社区、简介、缓存按钮
            gone(tvBookReadCommunity, tvBookReadIntroduce, tvBookReadDownload)
            return
        }
        doAsync {
            val queryChapters = mPresenter.queryChapters(mBookId)
            uiThread {
                if(queryChapters!!.size<0||queryChapters.size==0){
                    mPresenter. getBookMixAToc(mBookId, "chapters")
                }else{
                    val convertChaptersBean = BookTransformer.locaBookChaptersConvertChaptersBean(queryChapters)
                    chapters.addAll(convertChaptersBean)
                    loadCurrentChapter()
                }
            }
        }
    }


    /**
     * 对顶部状态栏覆盖toolbar的情况做处理
     */
    fun initStatusBar(){
        val identifierId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if(identifierId>0){
            statusBarHeight =  resources.getDimensionPixelSize(identifierId)
        }
        status_hight.height = statusBarHeight
    }


    override fun initView() {
        //隐藏状态栏
        rlBookReadRoot.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        initPagerWidget()
        initAASet()
        initDayMode()
        initClickView()
        initRxbus()
    }

    private fun initDayMode() {
        val isNight = !SharedPreferencesUtil.getBoolean(ConsTantUtils.ISNIGHT, false)
        tvBookReadMode.text = getString(if (isNight)
            R.string.book_read_mode_day_manual_setting
        else
            R.string.book_read_mode_night_manual_setting)
        val drawable = ContextCompat.getDrawable(this, if (isNight)
            R.mipmap.ic_menu_mode_day_manual
        else
            R.mipmap.ic_menu_mode_night_manual)
        drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
        tvBookReadMode.setCompoundDrawables(null, drawable, null, null)
    }

    private fun initRxbus() {
        mPresenter.registerRxBus(ReadEvent::class.java, Consumer<ReadEvent> { t -> handleChannelMessage(t) })
        mPresenter.registerRxBus(DownloadMessageEvent::class.java, Consumer { t -> handleDownloadMessage(t) })
        mPresenter.registerRxBus(DownloadProgressEvent::class.java, Consumer { t -> handleDownloadProgress(t) })
    }

    private fun handleDownloadProgress(t: DownloadProgressEvent) {
        if (mBookId == t.bookId) {
            if (llBookReadBottom.visibility ==View.VISIBLE) { // 如果工具栏显示，则进度条也显示
                visible(flReadWidget,llBookReadTop)
                visible(tvDownloadProgress)
                // 如果之前缓存过，就给提示
                tvDownloadProgress.text = t.message
            } else {
                gone(tvDownloadProgress)
            }
        }
    }

    private fun handleDownloadMessage(t: DownloadMessageEvent) {
        if (llBookReadBottom.visibility == View.VISIBLE) { // 如果工具栏显示，则进度条也显示
            visible(flReadWidget,llBookReadTop)
            if (mBookId == t.bookId) {
                tvDownloadProgress.visibility = View.VISIBLE
                tvDownloadProgress.text = t.message
                if (t.isComplete) {
                    tvDownloadProgress.postDelayed({ gone(tvDownloadProgress) }, 2500)
                }
            }
        }
    }

    private fun handleChannelMessage(t: ReadEvent) {
        val mChapterBean = t.mChapterBean
        val bookId = t.mBookId
        val chapterFromLog = t.mCurrentChapter
        startRead=false
        if(FileUtils.hasChapterFile(bookId,chapterFromLog)!=null){
            loadChapterRead(null,chapterFromLog)
        }else{
            currentChapter=t.mCurrentChapter
            upDataView()
        }
        hideReadBar()
    }
    var isAutoLightness: Boolean=false
    var isThemeClick = false
    var mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val what = msg.what
            if(what==0){
                isThemeClick = false
            }
        }
    }
    private fun initAASet() {
        curTheme = SettingManager.getInstance()!!.getReadTheme()
        ThemeManager.setReaderTheme(curTheme,rlBookReadRoot)
        //字体调节
        seekbarFontSize.max=10
        val readFontSize = SettingManager.getInstance()?.getReadFontSize()
        val progress = ((ScreenUtils.pxToDpInt(readFontSize!!.toFloat()) - 12) / 1.7f).toInt()
        seekbarFontSize.progress = progress
        seekbarFontSize.setOnSeekBarChangeListener(SeekBarChangeListener())
        //亮度调节
        seekbarLightness.max=100
        seekbarLightness.setOnSeekBarChangeListener(SeekBarChangeListener())
        seekbarLightness.progress = SettingManager.getInstance()!!.getReadBrightness()
        isAutoLightness = ScreenUtils.isAutoBrightness(this)
        if (SettingManager.getInstance()!!.isAutoBrightness()) {
            startAutoLightness()
        } else {
            stopAutoLightness()
        }
        //是否用音量键翻页
        cbVolume.isChecked = SettingManager.getInstance()!!.isVolumeFlipEnable()
        cbVolume.setOnCheckedChangeListener(ChechBoxChangeListener())
        //是都自动调节亮度
        cbAutoBrightness.isChecked = SettingManager.getInstance()!!.isAutoBrightness()
        cbAutoBrightness.setOnCheckedChangeListener(ChechBoxChangeListener())
        //选择阅读背景
        var themes = ThemeManager.getReaderThemeData(curTheme)
        var gvAdapter = ReadThemeAdapter(themes , curTheme)
        RecyclerViewHelper.initRecycleViewH(this@ReadActivity,gvTheme,gvAdapter,false)
        gvAdapter.bindToRecyclerView(gvTheme)
        gvAdapter.setOnItemClickListener { adapter, view, position ->
            if(!isThemeClick){
                isThemeClick=true
                val selectCricleImageView = adapter.getViewByPosition(position, R.id.ivThemeBg) as SelectCricleImageView
                mHandler.sendEmptyMessageDelayed(0, selectCricleImageView.getEndTime()*1000.toLong())
                if(selectCricleImageView.getMDynamicAngle()==360f){
                    toast("已选择!")
                    return@setOnItemClickListener
                }
                var index = 0
                while (index<adapter.itemCount){
                    val viewByPosition = adapter.getViewByPosition(index, R.id.ivThemeBg) as SelectCricleImageView
                    if(viewByPosition.getMDynamicAngle()==360f){
                        viewByPosition.reversalAnimation()
                        viewByPosition.setMDynamicAngle(0f)
                    }
                    index++
                }
                selectCricleImageView.startAnimation()
                selectCricleImageView.setMDynamicAngle(360f)
                if(position<themes.size){
                    changedMode(false,position)
                }else{
                    changedMode(true,position)
                }
            }else{
                toast("让人家缓一缓嘛!")
            }
        }
    }

    private fun changedMode(isNight: Boolean, position: Int) {
        SharedPreferencesUtil.putBoolean(ConsTantUtils.ISNIGHT, isNight)
        AppCompatDelegate.setDefaultNightMode(if (isNight)
            AppCompatDelegate.MODE_NIGHT_YES
        else
            AppCompatDelegate.MODE_NIGHT_NO)

        if (position >= 0) {
            curTheme = position
        } else {
            curTheme = SettingManager.getInstance()!!.getReadTheme()
        }
        mPageWidget.setTheme(if (isNight) ThemeManager.NIGHT else curTheme)
        mPageWidget.setTextColor(ContextCompat.getColor(this, if (isNight) R.color.chapter_content_night else R.color.chapter_content_day),
                ContextCompat.getColor(this, if (isNight) R.color.chapter_title_night else R.color.chapter_title_day))

        tvBookReadMode.text = getString(if (isNight)
            R.string.book_read_mode_day_manual_setting
        else
            R.string.book_read_mode_night_manual_setting)
        val drawable = ContextCompat.getDrawable(this, if (isNight)
            R.mipmap.ic_menu_mode_day_manual
        else
            R.mipmap.ic_menu_mode_night_manual)
        drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
        tvBookReadMode.setCompoundDrawables(null, drawable, null, null)

        ThemeManager.setReaderTheme(curTheme, rlBookReadRoot)
    }

    private fun startAutoLightness() {
        SettingManager.getInstance()?.saveAutoBrightness(true)
        ScreenUtils.startAutoBrightness(this@ReadActivity)
        seekbarLightness.isEnabled = false
    }

    private fun stopAutoLightness() {
        SettingManager.getInstance()?.saveAutoBrightness(false)
        ScreenUtils.stopAutoBrightness(this@ReadActivity)
        val value = SettingManager.getInstance()!!.getReadBrightness()
        seekbarLightness.progress = value
        ScreenUtils.setScreenBrightness(value, this@ReadActivity)
        seekbarLightness.isEnabled = true
    }

    private fun initClickView() {
        tvBookReadToc.setOnClickListener(this)
        ivBack.setOnClickListener(this)
        tvBookReadCommunity.setOnClickListener(this)
        tvBookReadIntroduce.setOnClickListener(this)
        tvBookReadSource.setOnClickListener(this)
        tvBookReadMode.setOnClickListener(this)
        tvBookReadSettings.setOnClickListener(this)
        tvBookReadDownload.setOnClickListener(this)
        tvBookMark.setOnClickListener(this)
        ivBrightnessMinus.setOnClickListener(this)
        ivBrightnessPlus.setOnClickListener(this)
        tvFontsizeMinus.setOnClickListener(this)
        tvFontsizePlus.setOnClickListener(this)
        tvClear.setOnClickListener(this)
        tvAddMark.setOnClickListener(this)
//        tvClear.setOnClickListener(this)
//        tvClear.setOnClickListener(this)
    }
    override fun onClick(v: View?) {
        val id = v?.id
         when(v?.id){
             R.id.tvBookReadToc->initTocList()
             R.id.ivBack->finish()
             R.id.tvBookReadCommunity->toast("社区,稍等!")
             R.id.tvBookReadIntroduce->BookDetailInfoActivity.open(this@ReadActivity,mBookId)
             R.id.tvBookReadSource->toast("换源,稍等!")
             R.id.tvBookReadMode->{
                 //日夜间模式切换,待定
                 gone(rlReadAaSet, rlReadMark)
                 val isNight = !SharedPreferencesUtil.getBoolean(ConsTantUtils.ISNIGHT, false)
                 changedMode(isNight, -1)
             }
             R.id.tvBookReadSettings->{
                 if (rlReadAaSet.visibility==View.VISIBLE) {
                     gone(rlReadAaSet)
                 } else {
                     visible(rlReadAaSet)
                     gone(rlReadMark)
                 }
             }
             R.id.tvBookReadDownload->showDownLoadDialog()
             R.id.tvBookMark->{
                 if (llBookReadBottom.visibility==View.VISIBLE) {
                     if (rlReadMark.visibility==View.VISIBLE) {
                         gone(rlReadMark)
                     } else {
                         gone(rlReadAaSet)
                         updateMark()
                         visible(rlReadMark)
                     }
                 }
             }
             R.id.ivBrightnessMinus->showBrightnessMinus()
             R.id.ivBrightnessPlus->showBrightnessPlus()
             R.id.tvFontsizeMinus-> calcFontSize(seekbarFontSize.progress - 1)
             R.id.tvFontsizePlus->calcFontSize(seekbarFontSize.progress + 1)
             R.id.tvClear-> {
                 SettingManager.getInstance()?.clearBookMarks(mBookId)
                 updateMark()
             }
             R.id.tvAddMark->addBookMark()


         }
    }

    private fun showDownLoadDialog() {
        gone(rlReadAaSet)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("缓存多少章？")
                .setItems(arrayOf("后面五十章", "后面全部", "全部")) { _, which ->
                    when (which) {
                        0 -> DownloadBookService.post(DownloadEvent(mBookId, chapters, currentChapter + 1, currentChapter + 50))
                        1 -> DownloadBookService.post(DownloadEvent(mBookId, chapters, currentChapter + 1, chapters.size))
                        2 -> DownloadBookService.post(DownloadEvent(mBookId, chapters, 1, chapters.size))
                        else -> {
                        }
                    }
                }
        builder.show()
    }

    private fun updateMark() {
        var arrayList = SettingManager.getInstance()?.getBookMarks(mBookId)
        if (arrayList != null && arrayList.size > 0) {
            Collections.reverse(arrayList)
        }
        val mMarkList = ArrayList<BookMark>()
        if(!ListUtils.isEmpty(arrayList)){
            mMarkList.addAll(arrayList!!)
        }
        var mMarkAdapter = BookMarkAdapter(mMarkList)
        RecyclerViewHelper.initRecycleViewV(this@ReadActivity,lvMark,mMarkAdapter!!,false)
        mMarkAdapter.setOnItemClickListener { adapter, view, position ->
            val data = adapter.data as BookMark
            if(data!=null){
                mPageWidget.setPosition(intArrayOf(data.chapter,data.startPos,data.endPos))
                hideReadBar()
            }else{
                toast("书签无效!")
            }
        }
    }

    private fun showBrightnessMinus() {
        var curBrightness = SettingManager.getInstance()!!.getReadBrightness()
        if (curBrightness > 2 && !SettingManager.getInstance()!!.isAutoBrightness()) {
            curBrightness -= 2
            seekbarLightness.progress = curBrightness
            ScreenUtils.setScreenBrightness(curBrightness, this@ReadActivity)
            SettingManager.getInstance()!!.saveReadBrightness(curBrightness)
        }
    }
    private fun showBrightnessPlus() {
        var curBrightness = SettingManager.getInstance()!!.getReadBrightness()
        if (curBrightness < 99 && !SettingManager.getInstance()!!.isAutoBrightness()) {
            curBrightness += 2
            seekbarLightness.progress = (curBrightness)
            ScreenUtils.setScreenBrightness(curBrightness, this@ReadActivity)
            SettingManager.getInstance()!!.saveReadBrightness(curBrightness)
        }
    }

    private fun initTocList() {
        CatalogueListActivity.open(this, mBookId, currentChapter,title)
    }

    fun initPagerWidget(){
        if(SharedPreferencesUtil.getInt(ConsTantUtils.FLIP_STYLE,0)==0){
            mPageWidget = PageWidget(this, mBookId, chapters, ReadListener())
        }else{
            mPageWidget = OverlappedWidget(this, mBookId, chapters, ReadListener())
        }
        //电量时间广播
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        intentFilter.addAction(Intent.ACTION_TIME_TICK)
        registerReceiver(receiver, intentFilter)
        if (SharedPreferencesUtil.getBoolean(ConsTantUtils.ISNIGHT, false)) {
            mPageWidget.setTextColor(ContextCompat.getColor(this, R.color.chapter_content_night),
                    ContextCompat.getColor(this, R.color.chapter_title_night))
        }
        flReadWidget.removeAllViews()
        flReadWidget.addView(mPageWidget)
    }
    private fun addBookMark() {
        val readPos = mPageWidget.readPos
        val mark = BookMark()
        mark.chapter = readPos[0]
        mark.startPos = readPos[1]
        mark.endPos = readPos[2]
        if (mark.chapter >= 1 && mark.chapter <= chapters.size) {
            mark.title = chapters.get(mark.chapter - 1).title
        }
        mark.desc = mPageWidget.headLine
        if (SettingManager.getInstance()!!.addBookMark(mBookId, mark)) {
            toast("添加书签成功")
            updateMark()
        } else {
            toast("书签已存在")
        }
    }


    var outBook: Boolean=false

    override fun initInjector() {
        DaggerReadComponent.builder()
                .applicationComponent(getAppComponent())
                .readModule(ReadModule(this))
                .build()
                .inject(this)
        initStatusBar()
        val action = intent.action
        if(Intent.ACTION_VIEW == action) {
            var  uri  = intent.data
            if (uri != null) {
                initView()
                toast("请去书架页面扫描书籍!")
               return
            }
        }else{
            outBook=false
            bookBean = intent.getSerializableExtra(BOOK_BEAN)as Recommend.RecommendBooks
            title = bookBean!!.title!!
            mBookId = bookBean!!._id!!
            mIsFromSd = bookBean!!.isFromSD
        }

    }

    fun getInsideString(str: String, strStart: String, strEnd: String): String {
        if (str.lastIndexOf(strStart) < 0) {
            return ""
        }
        if (str.indexOf(strEnd) < 0) {
            return ""
        }
        val substring = str.substring(str.lastIndexOf(strStart) + strStart.length, str.indexOf(strEnd))
        return substring
    }
    override fun getContentView(): Int {
        statusBarColor = ContextCompat.getColor(this, R.color.reader_menu_bg_color)
        return R.layout.activity_read
    }
    companion object {
        var BOOK_BEAN = "bookbean"
        var CHAPTER_BEAN = "CHAPTERBEAN"
        fun open(mContext: Context?, recommendBooks: Recommend.RecommendBooks) {
            mContext?.startActivity<ReadActivity>(BOOK_BEAN to recommendBooks)
            (mContext as Activity).overridePendingTransition(R.anim.fade_entry, R.anim.fade_exit)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> if (tvBookReadReading.visibility==View.VISIBLE) {
                gone(tvBookReadReading, tvBookReadCommunity, tvBookReadIntroduce)
                finish()
                return true
            } else if (rlReadAaSet.visibility==View.VISIBLE) {
                gone(rlReadAaSet)
                return true
            } else if (llBookReadBottom.visibility==View.VISIBLE) {
                hideReadBar()
                return true
            } else if (!mPresenter.queryBook(mBookId)) {
                showJoinBookShelfDialog(bookBean!!)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                toggleReadBar()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (SettingManager.getInstance()!!.isVolumeFlipEnable()) {
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> if (SettingManager.getInstance()!!.isVolumeFlipEnable()) {
                return true
            }
            else -> {
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (SettingManager.getInstance()!!.isVolumeFlipEnable()) {
                mPageWidget.nextPage()
                return true// 防止翻页有声音
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (SettingManager.getInstance()!!.isVolumeFlipEnable()) {
                mPageWidget.prePage()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showJoinBookShelfDialog(recommendBooks: Recommend.RecommendBooks) {
        AlertDialog.Builder(this@ReadActivity)
                .setTitle(getString(R.string.book_read_add_book))
                .setMessage(getString(R.string.book_read_would_you_like_to_add_this_to_the_book_shelf))
                .setPositiveButton(getString(R.string.book_read_join_the_book_shelf)) { dialog, which ->
                    dialog.dismiss()
                    recommendBooks.recentReadingTime = StringUtils.getCurrentTimeString(StringUtils.FORMAT_DATE_TIME)
                    AppApplication.instance.mRxBus?.post(ReadEvent(true,recommendBooks))
                    finish()
                }
                .setNegativeButton(getString(R.string.book_read_not)) { dialog, which ->
                    dialog.dismiss()
                    finish()
                }
                .create()
                .show()
    }

    inner class ReadListener : OnReadStateChangeListener {
        override fun onChapterChanged(chapter: Int) {
                currentChapter = chapter
                // 加载前一节 与 后三节
                var i = chapter - 1
                while (i <= chapter + 3 && i <= chapters.size) {
                    if (i > 0 && i != chapter
                            && FileUtils.hasChapterFile(mBookId, i) == null) {
                        if(chapters.get(i - 1).link==null){
                            mPresenter. getBookMixAToc(mBookId, "chapters")
                        }else{
                            mPresenter.getChapterRead(chapters.get(i - 1).link!!, i)
                        }
                    }
                    i++
                }
        }

        override fun onPageChanged(chapter: Int, page: Int) {

        }

        override fun onLoadChapterFailure(chapter: Int) {
            startRead=false
            if(FileUtils.hasChapterFile(mBookId,chapter)==null){
                mPresenter.getChapterRead(chapters.get(chapter - 1).link!!, chapter)
            }
        }

        override fun onCenterClick() {
            toggleReadBar()
        }

        override fun onFlip() {
            hideReadBar()
        }

    }
     //电量时间广播
    inner class Receiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (mPageWidget != null) {
                if (Intent.ACTION_BATTERY_CHANGED == intent.action) {
                    val level = intent.getIntExtra("level", 0)
                    mPageWidget.setBattery(100 - level)
                } else if (Intent.ACTION_TIME_TICK == intent.action) {
                    mPageWidget.setTime(sdf.format(Date()))
                }
            }
        }
    }

    @Synchronized
    private fun toggleReadBar() {
        if (llBookReadTop.visibility == View.VISIBLE) {
            hideReadBar()
        } else {
            showReadBar()
        }
    }
    @Synchronized
    private fun hideReadBar(){
        if(rlReadAaSet.visibility==View.VISIBLE||tvDownloadProgress.visibility==View.VISIBLE||
                rlReadMark.visibility==View.VISIBLE){
            gone(tvDownloadProgress, rlReadAaSet,rlReadMark)
        }
        getMeasureHeight(llBookReadBottom)
        getMeasureHeight(llBookReadTop)
        val bookReadTopHeight= llBookReadTop.measuredHeight.toFloat()
        val measuredHeight = llBookReadBottom.measuredHeight.toFloat()
        val point = Point()
        windowManager.defaultDisplay.getSize(point)
        val height = point.y.toFloat()
        val llBookReadBottomGone = ValueAnimator.ofFloat(measuredHeight)
        llBookReadBottomGone.addUpdateListener {
            val animatedValue = it.animatedValue as Float
            llBookReadBottom.translationY=animatedValue
        }
        llBookReadBottomGone.duration = 500
        llBookReadBottomGone.addListener(object : Animator.AnimatorListener{
            override fun onAnimationRepeat(animation: Animator?) {

            }

            override fun onAnimationEnd(animation: Animator?) {
                gone(llBookReadTop,llBookReadBottom)
            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationStart(animation: Animator?) {
            }
        })
        llBookReadBottomGone.start()
        setAnimationForView(llBookReadTop,"translationY",0f,-bookReadTopHeight)

        rlBookReadRoot.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    }
    @Synchronized
    private fun showReadBar(){
        getMeasureHeight(llBookReadBottom)
        getMeasureHeight(llBookReadTop)
        val measuredHeight = llBookReadBottom.measuredHeight.toFloat()
        val bookReadTopHeight= llBookReadTop.measuredHeight.toFloat()
        setAnimationForView(llBookReadBottom,"translationY",measuredHeight,0f)
        setAnimationForView(llBookReadTop,"translationY",-bookReadTopHeight,0f)
        visible(llBookReadBottom,llBookReadTop)
        //显示状态栏
        rlBookReadRoot.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private fun setAnimationForView(view:View,prop:String,start: Float,end: Float) {
        val animatorYReadBottom = ObjectAnimator.ofFloat(view, prop,
                start, end)
        animatorYReadBottom.duration = 500
        animatorYReadBottom.start()
    }

    private fun getMeasureHeight(view:View) {
        val w = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(w, h)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        mPresenter.unregisterRxBus()
        if (isAutoLightness) {
            ScreenUtils.startAutoBrightness(this@ReadActivity)
        } else {
            ScreenUtils.stopAutoBrightness(this@ReadActivity)
        }
    }
    inner class SeekBarChangeListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (seekBar?.id == seekbarFontSize.id && fromUser) {
                calcFontSize(progress)
            } else if (seekBar?.id == seekbarLightness.id && fromUser
                    && !SettingManager.getInstance()?.isAutoBrightness()!!) { // 非自动调节模式下 才可调整屏幕亮度
                ScreenUtils.setScreenBrightness(progress, this@ReadActivity)
                SettingManager.getInstance()?.saveReadBrightness(progress)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {

        }

    }

    private fun calcFontSize(progress: Int) {
    // progress range 1 - 10
        if (progress >= 0 && progress <= 10) {
            seekbarFontSize.progress = progress
            mPageWidget.setFontSize(ScreenUtils.dpToPxInt(12 + 1.7f * progress))
        }
    }
    inner class ChechBoxChangeListener : CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
            if (buttonView?.id == cbVolume.id) {
                SettingManager.getInstance()?.saveVolumeFlipEnable(isChecked)
            } else if (buttonView?.id == cbAutoBrightness.id) {
                if (isChecked) {
                    startAutoLightness()
                } else {
                    stopAutoLightness()
                }
            }
        }

    }




}
