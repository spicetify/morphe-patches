.class public final Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuBridge;
.super Ljava/lang/Object;

# Hooks T1 and T2 pass each context menu's frozen item list here, with the menu's CollectionTrack or
# CollectionArtist, just before the menu model (Lp/krj;) is built from it. They get back a copy with
# one Spotify menu item (Lp/jpj;) per {id, title} that ExtensionMenus gives, with Encore's trash icon,
# or the original list when there is nothing to add or anything fails.

.method public static track(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
    .locals 1
    const/4 v0, 0x0
    invoke-static {p0, p1, v0}, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuBridge;->append(Ljava/util/List;Ljava/lang/Object;Z)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method

.method public static artist(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
    .locals 1
    const/4 v0, 0x1
    invoke-static {p0, p1, v0}, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuBridge;->append(Ljava/util/List;Ljava/lang/Object;Z)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method

# Lp/jpj; takes exactly one title (the String, with titleRes null) and one icon. Its action is new
# Lp/ipj;(new Lp/zoj;(1), 1, hpj, MenuAction): category 0 throws in Lp/x2b;->B, and hpj, which only
# feeds analytics, can't be null.
# hpj is always new Lp/hpj;(Lp/lq11;->b, Lp/lq11;->c), the pair Lp/qj61;->b gives songdna_about. It
# logs a tap as a constant ui_navigate hit to spotify:internal:songdna:about, with no entity of ours.
# Copying another item's hpj would log whatever that item logs: in the artist menu, the first item's
# is a follow or unfollow of that artist.
# 13 locals keep p0 to p2 at v13 to v15, where non-range invokes can reach them.
.method private static append(Ljava/util/List;Ljava/lang/Object;Z)Ljava/util/List;
    .locals 13
    :try_start
    if-nez p2, :artist_items
    invoke-static {p1}, Lapp/spicetify/extension/spotify/extensions/ExtensionMenus;->trackItems(Ljava/lang/Object;)Ljava/util/List;
    move-result-object v2
    goto :have_items
    :artist_items
    invoke-static {p1}, Lapp/spicetify/extension/spotify/extensions/ExtensionMenus;->artistItems(Ljava/lang/Object;)Ljava/util/List;
    move-result-object v2
    :have_items
    invoke-interface {v2}, Ljava/util/List;->isEmpty()Z
    move-result v0
    if-nez v0, :unchanged

    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0, p0}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

    new-instance v1, Lp/hpj;
    sget-object v3, Lp/lq11;->b:Lp/lq11;
    sget-object v4, Lp/lq11;->c:Lp/lq11;
    invoke-direct {v1, v3, v4}, Lp/hpj;-><init>(Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)V

    invoke-interface {v2}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v2
    :next_item
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
    move-result v3
    if-eqz v3, :appended
    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, [Ljava/lang/String;
    const/4 v6, 0x0
    aget-object v4, v3, v6
    const/4 v6, 0x1
    aget-object v8, v3, v6

    # A tap hands ExtensionMenus the id it gave, still without the prefix added below.
    new-instance v11, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;
    invoke-direct {v11, v4, p1, p2}, Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuAction;-><init>(Ljava/lang/String;Ljava/lang/Object;Z)V
    const/4 v9, 0x1
    new-instance v10, Lp/zoj;
    invoke-direct {v10, v9}, Lp/zoj;-><init>(I)V
    new-instance v12, Lp/ipj;
    invoke-direct {v12, v10, v9, v1, v11}, Lp/ipj;-><init>(Lp/xa1;ILp/hpj;Lkotlin/jvm/functions/Function1;)V

    # Menu item ids must be unique, since Compose keys on them.
    const-string v3, "spicetify_"
    invoke-virtual {v3, v4}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v4

    sget-object v5, Lp/b1u;->c:Lp/b1u;

    # v3 item, v4 id, v5 icon, v6 alternative icon, v7 titleRes, v8 title, v9 accessibility
    # resource, v10 enabled, v11 decoration, v12 action.
    const/4 v6, 0x0
    const/4 v7, 0x0
    const/4 v9, 0x0
    const/4 v10, 0x1
    const/4 v11, 0x0
    new-instance v3, Lp/jpj;
    invoke-direct/range {v3 .. v12}, Lp/jpj;-><init>(Ljava/lang/String;Lp/v8u;Lp/gpj;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Integer;ZLp/e801;Lp/ipj;)V
    invoke-virtual {v0, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :next_item

    :appended
    return-object v0
    :try_end
    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :failed

    :unchanged
    return-object p0

    :failed
    move-exception v0
    const-string v1, "Spicetify"
    const-string v2, "Couldn't add the Spicetify menu items"
    invoke-static {v1, v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-object p0
.end method
