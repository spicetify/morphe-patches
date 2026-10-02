.class public final Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;
.super Lp/qf60;
.implements Lkotlin/jvm/functions/Function1;

# The click of one Spicetify item in Spotify's track or artist menu. Lp/ipj; check-casts its
# Function1 to Lp/qf60; (Kotlin's Lambda), hence the superclass, as in ModelFactory.

.field private final id:Ljava/lang/String;
.field private final target:Ljava/lang/Object;
.field private final artist:Z

.method public constructor <init>(Ljava/lang/String;Ljava/lang/Object;Z)V
    .locals 1
    const/4 v0, 0x1
    invoke-direct {p0, v0}, Lp/qf60;-><init>(I)V
    iput-object p1, p0, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;->id:Ljava/lang/String;
    iput-object p2, p0, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;->target:Ljava/lang/Object;
    iput-boolean p3, p0, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;->artist:Z
    return-void
.end method

# Returns null, not Kotlin's Unit (Lp/x181;->a in this build): every caller of Lp/ipj;->d drops
# the result, the menu click at Lp/o1;->invokeSuspend index 797 and five others.
.method public invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 3
    :try_start
    iget-object v0, p0, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;->id:Ljava/lang/String;
    iget-object v1, p0, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;->target:Ljava/lang/Object;
    iget-boolean v2, p0, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;->artist:Z
    if-nez v2, :artist
    invoke-static {v0, v1}, Lapp/spicetify/extension/spotify/extensions/ExtensionMenus;->onTrackItem(Ljava/lang/String;Ljava/lang/Object;)V
    goto :done
    :artist
    invoke-static {v0, v1}, Lapp/spicetify/extension/spotify/extensions/ExtensionMenus;->onArtistItem(Ljava/lang/String;Ljava/lang/Object;)V
    :try_end
    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :failed
    :done
    const/4 v0, 0x0
    return-object v0

    :failed
    move-exception v0
    const-string v1, "Spicetify"
    const-string v2, "A Spicetify menu item failed"
    invoke-static {v1, v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    goto :done
.end method
