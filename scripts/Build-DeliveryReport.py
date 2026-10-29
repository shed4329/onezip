"""Compose the offline handover report from verified release evidence and original screenshots."""
from pathlib import Path
import base64
import os
import hashlib
import html
import json
import shutil
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, Table, TableStyle, PageBreak, KeepTogether
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image as PILImage

ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / "target/onezip-delivery-20261029-r6"
OUT = RELEASE / "report"
OUT.mkdir(exist_ok=True)
pdfmetrics.registerFont(TTFont("YaHei", str(Path(os.environ["WINDIR"]) / "Fonts/msyh.ttc"), subfontIndex=0))
W = A4[0] - 84
styles = {
    "title": ParagraphStyle("Title", fontName="YaHei", fontSize=25, leading=34, spaceAfter=14),
    "h1": ParagraphStyle("Heading", fontName="YaHei", fontSize=17, leading=25, spaceAfter=12),
    "h2": ParagraphStyle("Subheading", fontName="YaHei", fontSize=12, leading=19, spaceBefore=9, spaceAfter=5),
    "p": ParagraphStyle("Body", fontName="YaHei", fontSize=10, leading=16, wordWrap="CJK", spaceAfter=9),
    "small": ParagraphStyle("Small", fontName="YaHei", fontSize=8.5, leading=13, wordWrap="CJK", spaceAfter=7, textColor=colors.HexColor("#555555")),
    "code": ParagraphStyle("Code", fontName="YaHei", fontSize=8, leading=12, spaceAfter=7),
    "cell": ParagraphStyle("Cell", fontName="YaHei", fontSize=8.8, leading=14, wordWrap="CJK"),
    "th": ParagraphStyle("TableHeading", fontName="YaHei", fontSize=8.8, leading=14, textColor=colors.white, wordWrap="CJK"),
}
pages = []
def page(title, *blocks): pages.append((title, list(blocks)))
def p(s): return ("p", s)
def h(s): return ("h2", s)
def small(s): return ("small", s)
def code(s): return ("code", s)
def picture(name, caption, maxheight=300): return ("image", name, caption, maxheight)
def table(headers, rows, widths): return ("table", headers, rows, widths)

page("OneZip 修改与交付报告",
    small("版本 0.6.0  ·  交付修订 r6  ·  2026年10月29日"),
    p("本次交付整理了代码结构、单文件设置、任务清理、ZIP 时间保存和 Java21 模块化打包，并修复 Windows 文件传参及按钮文字裁切。应用保持纯 Java 自行实现压缩与解压，不调用外部压缩引擎。"),
    p("交付包括 x64 便携包、x64 MSI 与 EXE 安装文件，以及 x86 便携包。211 项非 GUI 测试通过；实际模块化 GUI、原生启动器和解包迁移检查也已完成。32 位安装器的架构标记不正确，已隔离，不作为可用交付件。"),
    picture("01-home-native.png", "图 1  最终 x64 启动器的真实首页窗口  默认隔离设置", 310),
    h("阅读顺序"),
    p("第 2 至 5 页展示界面和外观验收；第 6 页说明代码修改；第 7 页列出测试范围；第 8 页记录体积与性能边界；第 9 页提供启动方式、交付路径和校验。"),
    small("完整原始截图共 13 张，随报告放在 screenshots 目录。除标注的原生窗口截图外，其余为实际 JavaFX 场景快照。修复前图片单独标注；本轮未使用生成图替代软件截图。"))

page("压缩界面与按钮修复",
    p("保留统一添加入口、文件管理器式 Ctrl 多选、无左侧栏和默认折叠的高级选项。密码放在压缩设置；压缩级别、线程数和 ZIP 文件说明放在高级选项，说明文本区已扩大。"),
    picture("02-compress.png", "图 2  最终 r6 压缩页  工具栏按钮完整显示", 325),
    p("按钮按完整文字计算最小宽度，完整说明可通过 JavaFX Tooltip 查看。较窄窗口中工具栏横向滚动，设置区上下排列，避免把动作文字压成省略号。滚动区域与标题栏控制按钮分开预留空间。"),
    picture("09-before-toolbar.png", "图 3  修复前候选构建  工具栏高度不足造成内容裁切", 160),
    small("修复后窄窗口原图见 07-narrow-compress.png 和 08-narrow-extract.png。滚动视口边缘可能只显示相邻按钮的一部分，需要横向滚动；这与按钮自身文字被省略不同。"))

page("解压浏览与 Windows 文件打开",
    picture("03-extract.png", "图 4  最终解压浏览页  目录式列表和底部文件说明", 325),
    h("统一启动协议"),
    p("OneZip.exe 默认进入 GUI；直接传入压缩包路径会打开列表。含中文、日文、空格的路径原样保留，多文件分别打开窗口。只有首参数为 --cli 才进入命令行。OneZipCLI.exe 通过补上 --cli 调用同一个入口，没有独立的命令实现。"),
    h("ZIP 时间修复"),
    p("旧写入器把 DOS 日期固定为 1980 年。现在写入源文件 UTC NTFS 扩展时间及本地 DOS 回退时间；单线程、多线程、AES 和添加文件都覆盖。读取优先使用扩展时间；解压在关闭文件后恢复修改时间，新建目录在子项完成后恢复。"),
    p("测试源时间为 2024-02-29T01:23:45.123456Z，台北界面显示 2024/02/29 09:23:45。已经写入旧包并丢失的原始时间无法推回，需要从源文件重新压缩。仅有 DOS 时间的包仍受两秒精度限制。"),
    small("13-open-with-native.png 为最终 EXE 接收中文空格路径的完整原生窗口截图。其他格式的完整时间和属性恢复未在本轮补齐。"))

page("进度反馈与设置",
    picture("04-progress-complete.png", "图 5  完成弹窗  百分比 文件序号 已处理数据 速度 用时和绿色进度条", 230),
    p("取消时显示红色进度条；关闭按钮等待实际后台清理和回滚完成后才可用。完成后可定位并选中输出目标。短动作文字配合 Tooltip 保留完整说明，避免长句挤压按钮。"),
    picture("05-settings-general.png", "图 6  通用设置  智能解压实际绑定到任务行为", 285),
    small("智能解压、单次任务覆盖、目标冲突保护、错误密码清理、未执行任务密码副本清除及设置重启保留均有独立验收。截图中的零点零 MB 来自很小的测试文件，速度和数据量不是性能基准。"))

page("外观与本地化验收边界",
    picture("06-settings-appearance.png", "图 7  个性化设置  主题色 图片路径 程序内模糊和透明度", 300),
    table(["项目", "本轮结果", "范围"], [
        ["四语界面", "通过", "简体 繁体 日文 英文；两种架构文件打开与窄窗口按钮"],
        ["语言重启", "通过", "最终 GUI 保存语言并在新 JVM 中恢复繁体"],
        ["图片及程序内模糊", "状态回归通过", "应用指定图片、模糊开关及页面切换保存"],
        ["控件透明度", "状态回归通过", "调到 100% 后切页，保存值保留"],
        ["原生选图及完整视觉矩阵", "本轮未测", "未重做原生选择器取消、完整重启和逐像素对比度矩阵"],
        ["系统 Acrylic", "未支持", "方案已放弃；程序内图片模糊不作为系统毛玻璃"],
        ["Windows10 真机", "未测", "本机 Windows build 26300；不据此声称 Win10 认证"],
    ], [0.28, 0.21, 0.51]),
    small("图片路径为 <test image supplied locally>。imagegen 工具可用，本轮未生成设计参考图；界面沿用已确认的 Fluent 方角设计。原生文件选择器的自动化限制不作为功能失败。"))

page("代码整理与可靠性修改",
    h("分层与清理"),
    p("83 份活动生产 Java 文件纳入编译、lint、格式及压缩依赖检查。42 份旧原型、原生桥接和兼容门面保留在 legacy，不参与发包。旧 IDE 配置移入 legacy/ide，开发时导入 pom.xml。"),
    code("src/cn/shed4329/onezip/\n  Launcher.java / CliLauncher.java\n  application/       ArchiveTask\n  engine/            readers writers codecs workers\n  setting/           SettingsStore typed preferences\n  i18n/              ResourceBundle and four locales\n  themes/fxJava/fluent/  Model Controller FXML CSS"),
    p("ArchiveEngine 作为操作入口，ArchiveReader 作为读取策略，ArchiveReplacement 共享 ZIP 安全替换流程。engine 不导入 JavaFX、设置或本地化模块。密码和可变编解码工作区由各任务管理，CPU 密集工作使用有界平台线程。"),
    h("单文件设置与失败保护"),
    p("设置集中在 %LOCALAPPDATA%\\OneZip\\settings.properties，采用 UTF-8、稳定键排序和临时文件原子替换。写入失败保留旧文件和内存值；损坏配置不会被静默覆盖。保留未知键和旧 TXT 原件，不保存密码；多进程仍为最后写入者生效。"),
    h("ZIP 编辑与路径安全"),
    p("添加、删除保留未修改条目的原始压缩数据。文件说明编辑先写私有临时副本，再检查并替换原包；失败保留原包。拒绝 Windows junction 等重解析路径，阻止来源或解压父目录被重定向。取消等待工作线程退出后再清理。"),
    h("模块化与文档"),
    p("使用单个 JPMS 模块 cn.shed4329.onezip，配合 jlink、jpackage、真实 ControlsFX 和一致的 JavaFX21。修复具名模块的资源包和图片 URL 读取，保留许可证及应用行号。Javadoc 的结构与引用检查通过，缺失成员注释检查未启用。"),
    small("仍是单模块内部分层，未拆成 Maven 多模块。源目录仍为 src 与 tests，Java 与 FXML/CSS/图片尚未完全分离，旧空目录和历史 out 输出仍保留；不宣称结构已彻底清洗或每个成员都有完整 Javadoc。"))

page("验证结果与未测项目",
    table(["验收项", "结果", "证据与边界"], [
        ["非 GUI 测试", "通过", "211 项 33 套件；0 失败 0 错误 0 跳过；历史 GUI XML 不计入"],
        ["格式 编译 文档", "通过", "格式化、生产 lint、Javadoc、纯 Java 压缩依赖检查"],
        ["Windows 文件路径", "通过", "真实 Launcher.main；x64/x86 × 四语；中文空格路径及条目列表"],
        ["多文件及缺失路径", "通过", "分别开窗；缺失文件显示 GUI 错误并可确认关闭"],
        ["窄窗口", "通过", "实际 680 像素宽窗口；按钮不省略；工具栏不裁切上下内容"],
        ["模块化 GUI 流程", "通过", "最终 r6 ZIP/7z 压缩解压、语言保存重启及智能解压回归"],
        ["CLI 格式与 AES", "通过", "两种架构 11 种可写格式创建 列表 校验 解压；AES ZIP；r5 算法构建"],
        ["统一入口", "通过", "实际 EXE --cli 创建文件、CLI 转发、CMD、独立 JAR；最终版本校验"],
        ["便携 ZIP", "通过", "最终 r6 自压缩 自校验 自解包；两种架构各 196 个文件 SHA256 一致"],
        ["x64 安装文件", "构建与静态检查通过", "MSI 与 EXE 已生成；MSI 为 x64，带引号的文件关联命令存在"],
        ["安装 升级 卸载", "未测", "未在用户系统安装以改变关联；需独立环境继续验收"],
        ["x86 安装器", "失败 不交付", "打包后 MSI 仍声明 x64；错误候选已隔离，脚本加入拒绝发布保护"],
    ], [0.23, 0.22, 0.55]),
    p("最初沙箱拦截了临时文件替换和 junction 测试；在正常 Windows 权限下重跑后通过。测试工具的模态窗口关闭方式也已修正，不把自动化清理问题算作软件功能缺陷。"),
    small("x86 GUI 和 CLI 在本机 WOW64 下实测，未在真正的 32 位 Windows 或 Windows10 真机上认证。编解码器仍有 ZIP32 写入/编辑、7z 不支持加密写入、RAR 只读及部分变体限制；云盘占位路径兼容性未认证。"))

page("体积与性能记录",
    table(["交付物", "大小", "说明"], [
        ["x64 便携 ZIP", "47.06 MB", "内含裁剪 Java21 运行时；完整解压后使用"],
        ["x86 便携 ZIP", "43.71 MB", "真实 32 位运行时和启动器；WOW64 实测"],
        ["x64 MSI / EXE", "47.88 / 48.47 MB", "当前用户安装方案；未做安装 升级 卸载实测"],
        ["独立 all.jar / 模块 JAR", "11.12 / 0.61 MB", "前者供 java -jar，后者供具名模块打包"],
        ["x64 / x86 裁剪运行时", "66.00 / 57.00 MB", "不含应用；相同条件全模块 x64 对照约 189.7 MB"],
    ], [0.35, 0.23, 0.42]),
    h("已有优化的测量"),
    p("前一工程整理阶段使用三轮交错测量，默认首页、无壁纸、隔离设置：原版工作集均值 238.1 MB，私有提交 483.5 MB；裁剪包为 213.7 MB 与 267.0 MB。主要收益来自 -Xms32m；工作集、私有提交与 Java 堆不是同一个指标。"),
    p("最终 r6 的一次原生首页检查为约 3.53 秒显示窗口，工作集 213.1 MB，私有提交 258.9 MB。这是单次样本，不作为新的平均值或速度承诺。最大堆与 GC 保留默认策略，没有为 CPU 编解码换用虚拟线程。"),
    table(["预热后操作", "整理前中位数", "r2 优化后中位数"], [
        ["ZIP 压缩", "21.76 ms", "18.93 ms"],
        ["ZIP 解压", "12.92 ms", "14.20 ms"],
        ["7z 压缩", "119.17 ms", "115.46 ms"],
        ["7z 解压", "66.55 ms", "66.43 ms"],
    ], [0.38, 0.31, 0.31]),
    small("上表来自此前 r2 的 2 MiB 固定混合数据，4 workers、5 级，各项至少预热 8 次且 4 秒，再计 7 次；校验和清理不计时。本轮没有重跑完整性能矩阵，不把它标成 r6 新基准。ZIP 时间属性恢复增加了小包文件系统写入成本；压缩包增加的 360 bytes 是时间元数据。表中 MB 均为十进制。"))

hashes = []
for line in (RELEASE / "ARTIFACT-SHA256.txt").read_text(encoding="utf-8-sig").splitlines():
    digest, name = line.split("  ", 1)
    hashes.append((name, digest))
page("启动方式与交付清单",
    code('OneZip.exe\nOneZip.exe "example archive.zip"\nOneZip.exe --cli c backup.zip source.txt\nOneZipCLI.exe c backup.zip source.txt\njava -jar onezip-0.6.0-SNAPSHOT-all.jar --cli --help'),
    p("便携版先完整解压，再启动 OneZip.exe；无需安装 Java。需要稳定终端显示或交互密码输入时使用 OneZipCLI.exe。安装版选择 windows-x64/installers 中的 MSI 或 EXE 之一；它们安装同一应用，尚未代码签名。独立 JAR 需要 Java21。"),
    small("交付根目录  target/onezip-delivery-20261029-r6"),
    h("SHA256 校验"),
    *sum(([small(name), code(digest)] for name, digest in hashes), []),
    h("源码与构建"),
    p("源码工程位于 <repository root>。构建脚本为 scripts/Build-Distribution.ps1 与 scripts/Build-Installers.ps1；交付中 build-scripts 保留审阅副本，运行构建应在完整源码工程内进行。x64 构建可使用 -InstallerTypes msi,exe；x86 只构建便携 app-image。"),
    small("API 文档位于 target/reports/apidocs/index.html；详细开发、设置和格式限制见源码 docs。报告包附原始截图、截图来源和哈希、测试计数。软件包逐文件校验见各 windows 架构目录中的 SHA256SUMS.txt。"))

story = []
html_pages = []
for index, (title, blocks) in enumerate(pages):
    story.append(Paragraph(html.escape(title), styles["title" if index == 0 else "h1"]))
    html_blocks = [f"<h1>{html.escape(title)}</h1>"]
    for block in blocks:
        kind = block[0]
        if kind in ("p", "h2", "small", "code"):
            text = html.escape(block[1]).replace("\n", "<br/>")
            story.append(Paragraph(text, styles[kind]))
            tag = {"p":"p", "h2":"h2", "small":"p", "code":"pre"}[kind]
            html_blocks.append(f'<{tag} class="{kind}">{html.escape(block[1])}</{tag}>')
        elif kind == "image":
            _, name, caption, maxheight = block
            path = OUT / "screenshots" / name
            with PILImage.open(path) as im: iw, ih = im.size
            scale = min(W / iw, maxheight / ih)
            image = Image(str(path), width=iw*scale, height=ih*scale)
            image.hAlign = "CENTER"
            story.append(KeepTogether([image, Spacer(1, 6), Paragraph(html.escape(caption), styles["small"])]))
            encoded = base64.b64encode(path.read_bytes()).decode("ascii")
            html_blocks.append(f'<figure><img src="data:image/png;base64,{encoded}" alt="{html.escape(caption)}"><figcaption>{html.escape(caption)}</figcaption></figure>')
        elif kind == "table":
            _, headers, rows, widths = block
            data = [[Paragraph(html.escape(str(value)), styles["th" if r == 0 else "cell"]) for value in row] for r, row in enumerate([headers] + rows)]
            t = Table(data, colWidths=[W*x for x in widths], repeatRows=1, hAlign="LEFT")
            t.setStyle(TableStyle([
                ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#303030")),
                ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, colors.HexColor("#F5F6F7")]),
                ("GRID", (0,0), (-1,-1), 0.5, colors.HexColor("#D9D9D9")),
                ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
                ("LEFTPADDING", (0,0), (-1,-1), 8), ("RIGHTPADDING", (0,0), (-1,-1), 8),
                ("TOPPADDING", (0,0), (-1,-1), 7), ("BOTTOMPADDING", (0,0), (-1,-1), 7),
            ]))
            story.extend([t, Spacer(1, 10)])
            html_blocks.append('<table><thead><tr>'+''.join(f'<th>{html.escape(str(x))}</th>' for x in headers)+'</tr></thead><tbody>'+''.join('<tr>'+''.join(f'<td>{html.escape(str(x))}</td>' for x in row)+'</tr>' for row in rows)+'</tbody></table>')
    html_pages.append('<section>'+''.join(html_blocks)+'</section>')
    if index < len(pages)-1: story.append(PageBreak())

def footer(c, doc):
    c.saveState(); c.setFont("YaHei", 8); c.setFillColor(colors.HexColor("#666666"))
    c.drawString(42, 23, "OneZip 0.6.0  r6  2026年10月29日")
    c.drawRightString(A4[0]-42, 23, str(doc.page)); c.restoreState()

pdf = OUT / "OneZip-修改与交付报告-20261029.pdf"
doc = SimpleDocTemplate(str(pdf), pagesize=A4, rightMargin=42, leftMargin=42, topMargin=39, bottomMargin=40, title="OneZip 修改与交付报告", author="OneZip", pageCompression=1)
doc.build(story, onFirstPage=footer, onLaterPages=footer)
css = '''body{margin:0;background:#eceef0;color:#191919;font:15px/1.8 "Microsoft YaHei",sans-serif}main{max-width:960px;margin:auto}section{background:white;margin:24px 0;padding:38px 48px}h1{font-size:26px;font-weight:500;margin:0 0 18px}h2{font-size:18px;font-weight:500;margin:24px 0 8px}p{margin:0 0 14px}.small,figcaption{font-size:12px;color:#555}pre{font:12px/1.8 Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}figure{margin:22px 0}img{max-width:100%;height:auto;display:block;margin:auto}figcaption{margin-top:8px}table{width:100%;border-collapse:collapse;font-size:13px;margin-bottom:18px}th{background:#303030;color:white;font-weight:400}th,td{border:1px solid #d9d9d9;padding:10px 12px;text-align:left}tr:nth-child(even){background:#f5f6f7}@media print{body{background:white}section{break-after:page;margin:0;padding:0}}'''
(OUT / "OneZip-修改与交付报告-20261029.html").write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>OneZip 修改与交付报告</title><style>'+css+'</style><main>'+''.join(html_pages)+'</main></html>', encoding="utf-8")
manifest = json.loads((OUT / "screenshot-manifest.json").read_text(encoding="utf-8-sig"))
extra = OUT / "screenshots/13-open-with-native.png"
if not any(x["file"] == "screenshots/13-open-with-native.png" for x in manifest):
    manifest.append({"file":"screenshots/13-open-with-native.png", "source":str(ROOT / "target/delivery-audit/r6-native-file-fit2/portable-home.png"), "sha256":hashlib.sha256(extra.read_bytes()).hexdigest().upper(), "kind":"native-window-capture"})
(OUT / "screenshot-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
print(pdf)
print("Report includes", len(pages), "planned pages and", len(manifest), "original screenshots")
