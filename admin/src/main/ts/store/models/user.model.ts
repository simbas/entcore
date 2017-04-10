import { Model } from 'toolkit'
import { UserDetailsModel } from './userdetails.model'
import { globalStore } from '..'

export class UserModel extends Model<UserModel> {

    constructor() {
        super({
            create: '/directory/api/user'
        })
        this.userDetails = new UserDetailsModel()
    }

    private _id: string
    get id(){ return this._id }
    set id(id) {
        this._id = id
        this.userDetails.id = id
    }
    type: string
    code: string
    login: string
    firstName: string
    lastName: string
    displayName: string
    source: string
    blocked: boolean
    aafFunctions: string[]
    functionalGroups: string[]
    structures: { id: string, name: string }[]
    classes: { id: string, name: string}[]
    duplicates: { id: string, firstName: string, lastName: string, code: string, structures: string[] }[]

    userDetails: UserDetailsModel

    visibleStructures() {
        return this.structures.filter(s => globalStore.structures.data.find(struct => struct.id === s.id))
    }

    invisibleStructures() {
        return this.structures.filter(s => globalStore.structures.data.every(struct => struct.id !== s.id))
    }

    addStructure(structureId: string) {
        return this.http.put(`/directory/structure/${structureId}/link/${this.id}`).then(() => {
            let targetStructure = globalStore.structures.data.find(s => s.id === structureId)
            if(targetStructure) {
                this.structures.push({id: targetStructure.id, name: targetStructure.name})
                if(targetStructure.users.data.length > 0)
                    targetStructure.users.data.push(this)
            }
        })
    }

    removeStructure(structureId: string) {
        return this.http.delete(`/directory/structure/${structureId}/unlink/${this.id}`).then(() => {
            this.structures = this.structures.filter(s => s.id !== structureId)
            let targetStructure = globalStore.structures.data.find(s => s.id === structureId)
            if(targetStructure && targetStructure.users.data.length > 0) {
               targetStructure.users.data = targetStructure.users.data.filter(u => u.id !== this.id)
            }
        })
    }

    async mergeDuplicate(duplicateId: string) : Promise<{ id: string } | { id: string, structure: string }> {
        await this.http.put(`/directory/duplicate/merge/${this.id}/${duplicateId}`)
        let duplicate = this.duplicates.find(d => d.id === duplicateId)
        this.duplicates = this.duplicates.filter(d => d.id !== duplicateId)
        try {
            await this.userDetails.sync()
            return { id: this.id }
        } catch(e) {
            return { id: duplicate.id, structure: duplicate.structures[0] }
        }
    }

    separateDuplicate(duplicateId: string) {
        return this.http.delete(`/directory/duplicate/ignore/${this.id}/${duplicateId}`).then(() => {
            let duplicate = this.duplicates.find(d => d.id === duplicateId)
            duplicate.structures.forEach(s => {
                let structure = globalStore.structures.data.find(struct => struct.id === s)
                if(structure && structure.users.data.length > 0) {
                    let dup = structure.users.data.find(u => u.id === duplicateId)
                    if(dup) dup.duplicates = dup.duplicates.filter(d => d.id !== this.id)
                }
            })
            this.duplicates = this.duplicates.filter(d => d.id !== duplicateId)
        })
    }
}