﻿import { model, notify, idiom as lang, toFormData, moment, _, $ } from 'entcore';

import { User } from './user';
import { Conversation } from './conversation';
import { quota } from './quota';
import { SystemFolder, UserFolder, Folder } from './folder';

import { Mix, Eventer, Selection, Selectable } from 'entcore-toolkit';

import http from 'axios';

export class Attachment {
    file: File;
    progress: {
        total: number,
        completion: number
    };
    id: string;
    filename: string;
    size: number;
    contentType: string;

    constructor(file: File) {
        this.file = file;
        this.progress = {
            total: 100,
            completion: 0
        }
    }
}

export class Mail implements Selectable {
    id: string;
    date: string;
    displayNames: string[];
    from: string;
    subject: string;
    body: string;
    to: User[];
    cc: User[];
    unread: boolean;
    state: string;
    parentConversation: Mail;
    newAttachments: FileList;
    loadingAttachments: Attachment[];
    attachments: Attachment[];
    eventer = new Eventer();
    selected: boolean;
    allowReply: boolean;
    allowReplyAll: boolean;

    constructor(id?: string) {
        this.id = id;
        this.loadingAttachments = [];
        this.attachments = [];
        this.allowReply = true;
        this.allowReplyAll = true;
    }

    isUserAuthor(): boolean {
        return this.from === model.me.userId;
    }

    getSystemFolder(): string {
        if (Conversation.instance.currentFolder.getName() !== 'OUTBOX' && (this.isMeInsideGroup(this.to) || this.isMeInsideGroup(this.cc)) && this.state === "SENT")
            return 'INBOX';
        if (Conversation.instance.currentFolder.getName() !== 'INBOX' && this.isUserAuthor() && this.state === "SENT")
            return 'OUTBOX';
        if (this.from === model.me.userId && this.state === "DRAFT")
            return 'DRAFT';
        return '';
    }

    matchSystemIcon(): string {
        const systemFolder = this.getSystemFolder();
        if (systemFolder === "INBOX")
            return this.getInSystemIcon();
        if (systemFolder === "OUTBOX")
        return this.getOutSystemIcon();
        if (systemFolder === "DRAFT")
            return 'draft';
        return '';
    }

    getInSystemIcon(): string {
        return 'mail-in';
    }

    getOutSystemIcon(): string {
        return 'mail-out';
    }

    isAvatarGroup(systemFolder: string): boolean {
        if (systemFolder === "INBOX")
            return false;
        return this.to.length > 1 || this.isRecipientGroup();
    }

    isAvatarUnknown(systemFolder: string): boolean {
        if (systemFolder === "INBOX" && !this.from)
            return true;
        if (systemFolder === "OUTBOX" && this.to.length === 1 && !this.to[0])
            return true;
        return this.to.length === 0;
    }

    isAvatarAlone(): boolean {
        const systemFolder = this.getSystemFolder();
        if (systemFolder === "INBOX")
            return true;
        return this.to.length === 1 && !this.isRecipientGroup();
    }

    matchAvatar(): string {
        const systemFolder = this.getSystemFolder();
        if (this.isAvatarGroup(systemFolder))
            return '/img/illustrations/group-avatar.svg?thumbnail=100x100';
        if (this.isAvatarUnknown(systemFolder))
            return '/img/illustrations/unknown-avatar.svg?thumbnail=100x100';
        if (this.isAvatarAlone()) {
            var id = systemFolder === "INBOX" ? this.from : this.to[0];
            return '/userbook/avatar/' + id + '?thumbnail=100x100';
        }
        return '';
    }

    isUnread(currentFolder: Folder): boolean {
        const systemFolder = this.getSystemFolder();
        return this.unread && (systemFolder === 'INBOX' || currentFolder.getName() === 'INBOX');
    }

    isRecipientGroup() {
        var to = this.to[0];
        if (!to)
            return false;
        if (!(to instanceof User)) {
            to = this.map(to);
        }
        return to.isAGroup();
    };

    isMeInsideGroup(list) {
        if(list[0] instanceof User){
            for (let user of list) {
                if (model.me.groupsIds.indexOf(user.id) !== -1 || user.id === model.me.userId)
                    return true;
            }
        }else {
            if (list.indexOf(model.me.userId) !== -1)
                return true;

            for (let id of list) {
                if (model.me.groupsIds.indexOf(id) !== -1)
                    return true;
            }
        }
        return false;
    }

    setMailSignature(signature: string){
        if(!this.body)
            this.body='';
        this.body = this.body + '<div><br></div><div class="signature new-signature">' + signature + '</div>'
    }

    setMailContent(origin: Mail, mailType: string, compile, sanitize, $scope, signature, copyReceivers?: boolean): Promise<any> {
        if (origin.subject.indexOf(format[mailType].prefix) === -1) {
            this.subject = lang.translate(format[mailType].prefix) + " " + origin.subject;
        }
        else {
            this.subject = origin.subject;
        }

        if (copyReceivers) {
            this.cc = origin.cc;
            this.to = origin.to;
        }

        return new Promise((resolve, reject) => {
            this.body = '<div><br></div><div class="signature new-signature">' + signature + '</div>' +
                format[mailType].content + '<blockquote>' + origin.body + '</blockquote>';
            const tempElement = compile(format[mailType].content)($scope);
            setTimeout(function(){
                this.body = $(document.createElement('div')).append(tempElement)[0].outerHTML + '<blockquote>' + this.body + '</blockquote>';
                tempElement.remove()
                resolve();
            }, 0)
        });
    }

    getSubject(){
        return this.subject ? this.subject : lang.translate('nosubject');
    }

    notifDate() {
        return moment(parseInt(this.date)).calendar();
    };

    longDate() {
        return moment(parseInt(this.date)).format('dddd DD MMMM YYYY')
    };

    isToday() {
        return moment(parseInt(this.date)).isSame(moment().startOf('day'), 'day');
    }

    isYesterday() {
        return moment(parseInt(this.date)).isSame(moment().subtract(1, 'day'), 'day');
    }

    isMoreThanYesterday() {
        return !this.isToday() && !this.isYesterday();
    }

    getHours() {
        return moment(parseInt(this.date)).format('HH');
    }

    getMinutes() {
        return moment(parseInt(this.date)).format('mm');
    }

    getDate() {
        return moment(parseInt(this.date)).format('dddd D MMMM YYYY');
    }

    sender() {
        var that = this;
        return User.prototype.mapUser(this.displayNames, this.from);
    };

    map(id) {
        if (id instanceof User) {
            return id;
        }
        return User.prototype.mapUser(this.displayNames, id);
    };

    async updateAllowReply() {
        const systemFolder = this.getSystemFolder();

        // Reply
        var id = systemFolder === "INBOX" ? this.from : this.to[0];
        var exists;
        if (!id) // completely deleted user
            exists = false;
        else
            exists = await this.map(id).findData();
        this.allowReply = exists;

        // Reply all
        if (exists) {
            for (let to of this.to) {
                var receiver = this.map(to);
                exists = await receiver.findData();
                if (!exists)
                    break;
            }
        }
        this.allowReplyAll = exists;
    };

    async saveAsDraft(): Promise<any> {
            var that = this;
            var data: any = { subject: this.subject, body: this.body };
            data.to = _.pluck(this.to, 'id');
            data.cc = _.pluck(this.cc, 'id');

            var path = '/conversation/draft';
            if (this.id) {
                const response = await http.put(path + '/' + this.id, data);
                Mix.extend(this, response.data);
            }
            else {
                if (this.parentConversation) {
                    path += '?In-Reply-To=' + this.parentConversation.id;
                }
                let response = await http.post(path, data)
                Mix.extend(this, response.data);
            }
    };

    async send() {
        var data: any = { subject: this.subject, body: this.body };
        data.to = _.pluck(this.to, 'id');
        data.cc = _.pluck(this.cc, 'id');
        if (data.to.indexOf(model.me.userId) !== -1) {
            Conversation.instance.folders['inbox'].nbUnread++;
        }
        if (data.cc.indexOf(model.me.userId) !== -1) {
            Conversation.instance.folders['inbox'].nbUnread++;
        }
        var path = '/conversation/send?';
        if (!data.subject) {
            data.subject = lang.translate('nosubject');
        }
        if (this.id) {
            path += 'id=' + this.id + '&';
        }
        if (this.parentConversation) {
            path += 'In-Reply-To=' + this.parentConversation.id;
        }

        try{
            const response = await http.post(path, data);
            const result = response.data;
            Conversation.instance.folders['outbox'].mails.refresh();
            Conversation.instance.folders['draft'].mails.refresh();

            if (parseInt(result.sent) > 0) {
                this.state = "SENT";
                notify.info('mail.sent');
            }

            return {inactive : result.inactive, undelivered : result.undelivered};
        }
        catch(e){
            notify.error(e.response.data.error);
            return {};
        }
    };

    async open(forPrint? : boolean) {
        if(this.unread && this.state !== "DRAFT"){
            Conversation.instance.currentFolder.nbUnread --;
        }
        this.unread = false;
        let response = await http.get('/conversation/message/' + this.id)
        Mix.extend(this, response.data);
        this.to = this.to.map(user => (
            Mix.castAs(User, {
                id: user,
                displayName: this.displayNames.find(name => name[0] === user as any)[1]
            })
        ));

        this.cc = this.cc.map(user => (
            Mix.castAs(User, {
                id: user,
                displayName: this.displayNames.find(name => name[0] === user as any)[1]
            })
        ));
        if(!forPrint) {
            await Conversation.instance.folders['inbox'].countUnread();
            await this.updateAllowReply();
        }
    };

    async remove() {
        if(!this.id)
            return;
        if ((Conversation.instance.currentFolder as SystemFolder).folderName !== 'trash') {
            await http.put('/conversation/trash?id=' + this.id);
            Conversation.instance.currentFolder.mails.refresh();
            Conversation.instance.folders['trash'].mails.refresh();
        }
        else {
            await http.delete('/conversation/delete?id=' + this.id);
            Conversation.instance.folders['trash'].mails.refresh();
        }
    };

    async removeFromFolder() {
        return http.put('move/root?id=' + this.id)
    }

    async restore() {
        await http.put('/conversation/restore?id=' + this.id);
        Conversation.instance.folders['trash'].mails.refresh();
    }

    async move(destinationFolder) {
        await http.put('move/userfolder/' + destinationFolder.id + '?id=' + this.id);
        await Conversation.instance.currentFolder.mails.refresh();
        await Conversation.instance.folders.draft.mails.refresh();
    }

    async trash() {
        await http.put('/conversation/trash?id=' + this.id);
        await Conversation.instance.currentFolder.mails.refresh();
        await Conversation.instance.folders.draft.mails.refresh();
    }

    postAttachments($scope) {
        const promises: Promise<any>[] = [];
        for(let i = 0; i < this.newAttachments.length; i++){
            const targetAttachment = this.newAttachments[i];
            const attachmentObj = new Attachment(targetAttachment);
            this.loadingAttachments.push(attachmentObj)

            const formData = new FormData()
            formData.append('file', attachmentObj.file)

            const promise = http.post("message/" + this.id + "/attachment", formData, {
                onUploadProgress: (e: ProgressEvent) => {
                    if (e.lengthComputable) {
                        var percentage = Math.round((e.loaded * 100) / e.total);
                        attachmentObj.progress.completion = percentage;
                        $scope.$apply();
                    }
                }
            })
            .then(response => {
                this.loadingAttachments.splice(this.loadingAttachments.indexOf(attachmentObj), 1);
                attachmentObj.id = response.data.id;
                attachmentObj.filename = attachmentObj.file.name;
                attachmentObj.size = attachmentObj.file.size;
                attachmentObj.contentType = attachmentObj.file.type;
                this.attachments.push(attachmentObj);
                quota.refresh();
                $scope.$apply();
            })
            .catch(e => {
                this.loadingAttachments.splice(this.loadingAttachments.indexOf(attachmentObj), 1);
                notify.error(e.response.data.error);
            });

            promises.push(promise)
        }

        return Promise.all(promises);
    }


    async deleteAttachment(attachment) {
        this.attachments.splice(this.attachments.indexOf(attachment), 1);
        await http.delete("message/" + this.id + "/attachment/" + attachment.id);
        quota.refresh();
    }
}

export class Mails {
    pageNumber: number;
    api: { get: string, put: string, post: string, delete: string };
    full: boolean;
    selection: Selection<Mail>;
    userFolder: UserFolder;
    loading: boolean;

    push(item: Mail){
        this.all.push(item);
    }

    get all(): Mail[]{
        return this.selection.all;
    }

    constructor(api: { get: string, put: string, post: string, delete: string } | UserFolder) {
        if(api instanceof UserFolder){
            this.userFolder = api;
        }
        else{
            this.api = api;
        }
        this.loading = false;
        this.selection = new Selection<Mail>([]);
    }

    async removeFromFolder(){
        await http.put('move/root?' + toFormData({ id: _.pluck(this.selection.selected, 'id') }))
    }

    addRange(arr: Mail[], selectAll: boolean){
        if(!(arr[0] instanceof Mail)){
            arr.forEach(d => {
                var m = Mix.castAs(Mail, d);
                if (selectAll)
                    m.selected = true;
                this.all.push(m);
            });
        }
        else{
            arr.forEach(m => {
                if (selectAll)
                    m.selected = true;
                this.all.push(m);
            });
        }
    }

    async sync(data?: { pageNumber?: number, searchText?: string, emptyList?: boolean, filterUnread?: boolean, selectAll?: boolean }){
        this.loading = !data || !data.pageNumber || data.pageNumber == 0 || data.searchText != undefined && data.pageNumber == 0;
        if(this.userFolder){
            await this.userFolderSync(data);
        }
        else{
            await this.apiSync(data);
        }
        this.loading = false;
    }

    async userFolderSync(data?: { pageNumber?: number, searchText?: string, emptyList?: boolean, filterUnread?: boolean, selectAll?: boolean }){
        if(!data){
            data = {};
        }
        if (!data.pageNumber) {
            data.pageNumber = 0;
        }
        if(!data.searchText){
            data.searchText = "";
        }else {
            data.searchText += "&search=" + data.searchText;
        }
        if (!data.filterUnread) {
            data.filterUnread = false;
        }
        if (!data.selectAll) {
            data.selectAll = false;
        }
        const response = await http.get('/conversation/list/' + this.userFolder.id + '?restrain=&page=' + data.pageNumber + "&unread=" + data.filterUnread + data.searchText);
        if(data.emptyList !== false){
            this.all.splice(0, this.all.length);
        }
        response.data.forEach(m => {
            if (data.selectAll)
                m.selected = true;
            this.all.push(Mix.castAs(Mail, m))
        });
        if (response.data.length === 0) {
            this.full = true;
        }
    }

    async apiSync(data?: { pageNumber?: number, searchText?: string, emptyList?: boolean, filterUnread?: boolean, selectAll?: boolean }): Promise<void>{
        if (!data) {
            data = {};
        }
        if (!data.pageNumber) {
            data.pageNumber = 0;
        }
        if(!data.searchText){
            data.searchText = "";
        }else {
            data.searchText = "&search=" + data.searchText;
        }
        if (!data.filterUnread) {
            data.filterUnread = false;
        }
        if (!data.selectAll) {
            data.selectAll = false;
        }
        let response = await http.get(this.api.get + '?page=' + data.pageNumber + "&unread=" + data.filterUnread + data.searchText);
        if(data.emptyList !== false){
            this.all.splice(0, this.all.length);
        }

        this.addRange(response.data, data.selectAll);
        if (response.data.length === 0) {
            this.full = true;
        }
    }

    refresh() {
        this.pageNumber = 0;
        this.full = false;
        return this.sync();
    }

    async toTrash() {
        await http.put('/conversation/trash?' + toFormData({ id: _.pluck(this.selection.selected, 'id') }));
        Conversation.instance.folders.trash.mails.refresh();
        quota.refresh();
        this.selection.removeSelection();
    }

    removeSelection(){
        this.selection.removeSelection();
    }

    async moveSelection(destinationFolder) {
        await http.put('move/userfolder/' + destinationFolder.id + '?' + toFormData({ id: _.pluck(this.selection.selected, 'id') }));
    }

    async toggleUnread(unread) {
        // Unselect mails that are not from inbox
        var selected = [];
        var folder = '';

        this.selection.selected.forEach(mail => {
            folder = mail.getSystemFolder();
            if (folder === 'INBOX' || folder === 'OUTBOX') {
                selected.push(mail);
            }
        });
        if (selected.length === 0)
            return;

        var paramsIds = toFormData({ id: _.pluck(selected, 'id') });
        var paramUnread = `unread=${unread}`;

        try{
            await http.post(`/conversation/toggleUnread?${paramsIds}&${paramUnread}`);
            quota.refresh();
            selected.forEach(mail => mail.unread = unread);
        }
        catch(e){
            notify.error(e.response.data.error);
        }

    }
}

let mailFormat = {
    reply: {
        prefix: 'reply.re',
        content: ''
    },
    transfer: {
        prefix: 'reply.fw',
        content: ''
    }
};

http.get('/conversation/public/template/mail-content/transfer.html').then(response => {
    format.transfer.content = response.data;
});

http.get('/conversation/public/template/mail-content/reply.html').then(response => {
    format.reply.content = response.data;
});

export const format = mailFormat;
