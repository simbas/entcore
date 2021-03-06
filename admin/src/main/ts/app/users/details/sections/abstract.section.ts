import { Input, ViewChild, ChangeDetectorRef } from '@angular/core'

import { UserDetailsModel, StructureModel, globalStore, UserModel } from '../../../core/store'
import { SpinnerService } from '../../../core/services'

export abstract class AbstractSection {

    constructor(){}

    get user(){ return this._user }
    set user(u: UserModel){
        this._user = u
        this.details = u.userDetails
        this.onUserChange()
    }
    protected _user : UserModel
    details: UserDetailsModel
    structure: StructureModel

    protected now : string = `${new Date().getFullYear()}-${new Date().getMonth() + 1}-${new Date().getDate()}`
    emailPattern = /^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$/

    protected getStructure(id: string) {
        return globalStore.structures.data.find(s => s.id === id)
    }

    protected abstract onUserChange()

}