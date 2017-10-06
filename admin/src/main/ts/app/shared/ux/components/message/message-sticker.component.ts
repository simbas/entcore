import { Component, Input,ChangeDetectorRef, ViewChild, OnInit } from '@angular/core'
import { ComponentDescriptor, DynamicComponent } from '../../directives'
import { BundlesService } from 'sijil'
import { MessageBox, MessageType} from './message-box.component'

@Component({
    selector: 'message-sticker',
    template: `
    <i (click)="loadMessageBox()" class="fa {{icons[type]}} is-{{type}}"></i>
        <ng-template [dynamic-component]="newMessageBox()"></ng-template>
        `,
    styles: [`
        :host {
            display: inline;
            position: relative;
        }
        i { cursor : pointer; }
    `]
})
export class MessageSticker implements OnInit {
    constructor (
        private cdRef:ChangeDetectorRef)  {}

    @Input() type: MessageType;
    @Input() header:string;
    @Input() messages:string[];
    @ViewChild(DynamicComponent) dComponent: DynamicComponent;

    readonly icons = {
        info : 'fa-question-circle',
        warning : 'fa-exclamation-circle',
        danger : 'fa-ban',
        success : 'fa-check-circle' 
    };
    
    ngOnInit():void { 
        if (this.type == undefined) {
            throw new Error('MessageSticker : type\' property must be set');
        }
    }

    newMessageBox():ComponentDescriptor {
        return new ComponentDescriptor(MessageBox, {
            type: this.type,
            header: this.header, 
            messages:this.messages,
            position:'absolute'
        });
    }

    loadMessageBox() : void {
        this.dComponent.load();
    }

}